import pyttsx3
import speech_recognition as sr
import os
import subprocess as sp
from datetime import datetime
from decouple import config
from conv import random_text
from random import choice
import json

# --- Gemini Integration ---
import google.generativeai as genai
from google.generativeai.types import FunctionDeclaration
from google.api_core.exceptions import GoogleAPIError
# No ToolCodeResult import needed here, using genai.protos.Part directly

# Import all online functionalities (these will be our "tools")
from online import (
    find_my_ip,
    search_on_google,
    search_on_wikipedia,
    youtube,
    send_email,
    get_stock_price,
    get_best_credit_card_for_category,
    open_email_client,
    perform_web_search,
    open_website
)

# Initialize TTS engine
engine = pyttsx3.init()
engine.setProperty('volume', 1.0)
engine.setProperty('rate', 225)

# Set Microsoft David Desktop voice
voices = engine.getProperty('voices')
found_david = False
for voice in voices:
    if "David" in voice.name or "TTS_MS_EN-US_DAVID" in voice.id:
        engine.setProperty('voice', voice.id)
        found_david = True
        break
if not found_david:
    print("Warning: Microsoft David voice not found. Using default voice.")

# Get USER and BOT (Jarvis) names from .env file
USER = config('USER')
HOSTNAME = config('BOT')

# --- Conversation History for LLM (Gemini uses 'parts' in messages) ---
conversation_history = []


def speak(text):
    """Converts text to speech."""
    print(f"{HOSTNAME}: {text}")
    engine.say(text)
    engine.runAndWait()
    conversation_history.append({"role": "model", "parts": [{"text": text}]})


def greet_me():
    """Greets the user based on the time of day."""
    hour = datetime.now().hour
    if 0 <= hour < 12:
        speak(f"Good Morning {USER}")
    elif 12 <= hour < 16:
        speak(f"Good Afternoon {USER}")
    elif 16 <= hour < 18:
        speak(f"Good Evening {USER}")
    else:
        speak(f"Hello Mister {USER}")
    speak(f"I am {HOSTNAME}, how may I help you?")


def take_command():
    """Listens for user's voice input and converts it to text."""
    r = sr.Recognizer()
    with sr.Microphone() as source:
        print("Listening...")
        r.pause_threshold = 1
        audio = r.listen(source)

    try:
        print("Recognizing...")
        queri = r.recognize_google(audio, language='en-in')
        print(f"User said: {queri}")
        conversation_history.append({"role": "user", "parts": [{"text": queri}]})

    except sr.UnknownValueError:
        speak("Sorry, I couldn't understand what you said. Can you please repeat that?")
        queri = ''
    except sr.RequestError as e:
        speak(f"Could not request results from Google Speech Recognition service; {e}")
        queri = ''
    except Exception as e:
        print(f"An unexpected error occurred in take_command: {e}")
        speak("An unexpected error occurred. Please try again.")
        queri = ''
    return queri


# --- Define Tools for Gemini ---
tools = [
    FunctionDeclaration(
        name="perform_web_search",
        description="**CRITICAL**: Use this tool to get the most **up-to-date, real-time, or current information** from the internet, including **breaking news headlines, recent events, live data**, and anything beyond the LLM's training cutoff. This tool performs a live web search and provides snippets of results. **When this tool returns results, immediately synthesize them into a concise, spoken summary or a brief list of top headlines for the user.**", # MODIFIED DESCRIPTION
        parameters={
            "type": "OBJECT",
            "properties": {
                "query": {"type": "STRING", "description": "The specific search query for the web (e.g., 'latest stock market news', 'current weather in London', 'who won the recent election')."},
                "num_results": {"type": "NUMBER", "description": "The maximum number of search results to retrieve (default is 3, max 5)."}
            },
            "required": ["query"],
        },
    ),
    FunctionDeclaration(
        name="open_website",
        description="Opens a specific URL (web address) in the user's default web browser.",
        parameters={
            "type": "OBJECT",
            "properties": {
                "url": {"type": "STRING", "description": "The full URL to open (e.g., 'https://www.example.com')."}
            },
            "required": ["url"],
        },
    ),
    FunctionDeclaration(
        name="search_on_google",
        description="Opens the Google search page in a web browser for a given query. Use 'perform_web_search' if you need the search results returned to the LLM.",
        parameters={
            "type": "OBJECT",
            "properties": {
                "query": {"type": "STRING", "description": "The search query for Google."},
            },
            "required": ["query"],
        },
    ),
    FunctionDeclaration(
        name="search_on_wikipedia",
        description="Searches Wikipedia for a given query and returns a summary.",
        parameters={
            "type": "OBJECT",
            "properties": {
                "query": {"type": "STRING", "description": "The search term for Wikipedia."},
            },
            "required": ["query"],
        },
    ),
    FunctionDeclaration(
        name="youtube",
        description="Plays a video on YouTube for a given video name.",
        parameters={
            "type": "OBJECT",
            "properties": {
                "video": {"type": "STRING", "description": "The name of the video to play on YouTube."},
            },
            "required": ["video"],
        },
    ),
    FunctionDeclaration(
        name="send_email",
        description="Sends an email to a specified recipient with a subject and message. NOTE: This function requires manual input for the recipient's email address in the terminal due to security. The LLM will extract subject and message if provided.",
        parameters={
            "type": "OBJECT",
            "properties": {
                "receiver_add": {"type": "STRING", "description": "The email address of the recipient."},
                "subject": {"type": "STRING", "description": "The subject of the email."},
                "message": {"type": "STRING", "description": "The body content of the email."},
            },
            "required": ["receiver_add", "subject", "message"],
        },
    ),
    FunctionDeclaration(
        name="get_stock_price",
        description="Fetches the current stock price for a given company's ticker symbol (e.g., AAPL for Apple, GOOGL for Google).",
        parameters={
            "type": "OBJECT",
            "properties": {
                "ticker_symbol": {"type": "STRING", "description": "The ticker symbol of the company's stock."},
            },
            "required": ["ticker_symbol"],
        },
    ),
    FunctionDeclaration(
        name="get_best_credit_card_for_category",
        description="Recommends the best credit card for a given spending category (e.g., groceries, gas, restaurants, online shopping).",
        parameters={
            "type": "OBJECT",
            "properties": {
                "category_query": {"type": "STRING",
                                   "description": "The spending category to get a credit card recommendation for."},
            },
            "required": ["category_query"],
        },
    ),
    FunctionDeclaration(
        name="find_my_ip",
        description="Finds and returns the public IP address of the device.",
        parameters={"type": "OBJECT", "properties": {}, "required": []},
    ),
    FunctionDeclaration(
        name="open_command_prompt",
        description="Opens the Windows Command Prompt.",
        parameters={"type": "OBJECT", "properties": {}, "required": []},
    ),
    FunctionDeclaration(
        name="open_camera",
        description="Opens the default camera application on Windows.",
        parameters={"type": "OBJECT", "properties": {}, "required": []},
    ),
    FunctionDeclaration(
        name="open_microsoft_edge",
        description="Opens the Microsoft Edge web browser.",
        parameters={"type": "OBJECT", "properties": {}, "required": []},
    ),
    FunctionDeclaration(
        name="open_google_chrome",
        description="Opens the Google Chrome web browser.",
        parameters={"type": "OBJECT", "properties": {}, "required": []},
    ),
    FunctionDeclaration(
        name="open_email_client",
        description="Opens the user's default email client or Gmail in a web browser.",
        parameters={"type": "OBJECT", "properties": {}, "required": []},
    ),
]

# Map tool names to actual Python functions
available_functions = {
    "perform_web_search": perform_web_search,
    "open_website": open_website,
    "search_on_google": search_on_google,
    "search_on_wikipedia": search_on_wikipedia,
    "youtube": youtube,
    "send_email": send_email,
    "get_stock_price": get_stock_price,
    "get_best_credit_card_for_category": get_best_credit_card_for_category,
    "find_my_ip": find_my_ip,
    "open_command_prompt": lambda: os.system('start cmd'),
    "open_camera": lambda: sp.run('start microsoft.windows.camera:', shell=True),
    "open_microsoft_edge": lambda: os.startfile(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"),
    "open_google_chrome": lambda: os.startfile(r"C:\Program Files\Google\Chrome\Application\chrome.exe"),
    "open_email_client": open_email_client,
}

if __name__ == "__main__":
    try:
        GEMINI_API_KEY = config('GEMINI_API_KEY')
        genai.configure(api_key=GEMINI_API_KEY)

        model = genai.GenerativeModel(
            'gemini-1.5-flash',
            tools=tools
        )
        chat_session = model.start_chat(history=[])

    except Exception as e:
        print(f"Error initializing Gemini API or loading configuration: {e}")
        print("Please ensure GEMINI_API_KEY is set correctly in your .env file and you have network access.")
        exit()

    greet_me()
    if random_text:
        speak(choice(random_text))

    while True:
        query = take_command()

        if not query:
            continue

        if 'stop' in query.lower() or 'exit' in query.lower() or 'quit' in query.lower():
            hour = datetime.now().hour
            if 21 <= hour or hour < 6:
                speak(f"Good night Mister {USER}, take care")
            else:
                speak(f"Have a good day Mister {USER}")
            break

        try:
            print("DEBUG: Sending message to Gemini API...")
            response = chat_session.send_message(query)
            print(f"DEBUG: Received raw response from Gemini: {response}")

            if response.candidates and response.candidates[0].content.parts:

                tool_executed_this_turn = False

                for part in response.candidates[0].content.parts:
                    if part.function_call:
                        tool_executed_this_turn = True
                        function_call = part.function_call
                        function_name = function_call.name
                        function_to_call = available_functions.get(function_name)

                        print(f"DEBUG: Gemini wants to call function: {function_name}")

                        if function_to_call:
                            try:
                                function_args = {key: value for key, value in function_call.args.items()}
                                print(f"DEBUG: Function args: {function_args}")

                                if function_name == "send_email":
                                    receiver_add_llm = function_args.get("receiver_add")
                                    subject_llm = function_args.get("subject")
                                    message_llm = function_args.get("message")

                                    receiver_add = receiver_add_llm if receiver_add_llm else input(
                                        f"Jarvis: On what email address do you like me to send to, Mister {USER}? Please enter it in the terminal: ")
                                    subject = subject_llm if subject_llm else take_command()
                                    message = message_llm if message_llm else take_command()

                                    if receiver_add and subject and message:
                                        tool_output = send_email(receiver_add, subject, message)
                                        if tool_output:
                                            speak("I'm processing the email, please wait.")
                                        else:
                                            speak(
                                                "There was an issue sending the email. Please check the terminal for details.")
                                    else:
                                        tool_output = "Email sending aborted: Missing recipient, subject, or message."
                                        speak("It seems some information for the email was missing. Please try again.")

                                elif function_name in ["open_command_prompt", "open_camera", "open_microsoft_edge",
                                                       "open_google_chrome", "find_my_ip",
                                                       "open_email_client", "open_website"]:
                                    tool_output = function_to_call(**function_args)
                                    speak(f"Executing {function_name.replace('_', ' ')}.")

                                elif function_name == "youtube":
                                    video_name = function_args.get("video")
                                    if not video_name:
                                        speak("What video do you want to play on YouTube, sir?")
                                        video_name = take_command()
                                    if video_name:
                                        speak(f"Playing {video_name} on YouTube.")
                                        tool_output = function_to_call(video_name)
                                    else:
                                        tool_output = "No video specified for YouTube."

                                elif function_name == "search_on_google":
                                    query_for_google = function_args.get("query")
                                    if not query_for_google:
                                        speak(f"What do you want to search on Google, {USER}?")
                                        query_for_google = take_command()
                                    if query_for_google:
                                        speak(f"Opening Google for {query_for_google}")
                                        tool_output = function_to_call(query_for_google)
                                    else:
                                        tool_output = "No search query provided for Google."

                                elif function_name == "search_on_wikipedia":
                                    query_for_wiki = function_args.get("query")
                                    if not query_for_wiki:
                                        speak("What do you want to search on Wikipedia, sir?")
                                        query_for_wiki = take_command()
                                    if query_for_wiki:
                                        speak(f"Searching Wikipedia for {query_for_wiki}")
                                        tool_output = function_to_call(query_for_wiki)
                                    else:
                                        tool_output = "No search term provided for Wikipedia."

                                elif function_name == "get_stock_price":
                                    ticker = function_args.get("ticker_symbol")
                                    if not ticker:
                                        speak(
                                            "Which company's stock price do you want to know? Please tell me the ticker symbol.")
                                        ticker = take_command().upper()
                                    if ticker:
                                        speak(f"Fetching stock price for {ticker}")
                                        tool_output = function_to_call(ticker)
                                    else:
                                        tool_output = "No ticker symbol provided for stock price."

                                elif function_name == "get_best_credit_card_for_category":
                                    category = function_args.get("category_query")
                                    if not category:
                                        speak("What kind of purchase are you making, or what is the spending category?")
                                        category = take_command()
                                    if category:
                                        speak(f"Looking for the best card for {category}...")
                                        tool_output = function_to_call(category)
                                    else:
                                        tool_output = "No category provided for credit card recommendation."

                                elif function_name == "perform_web_search":
                                    speak("One moment, I am searching the web for that information.")
                                    tool_output = function_to_call(**function_args)
                                    # The LLM will then process this tool_output to formulate a response

                                else:
                                    tool_output = function_to_call(**function_args)


                                print(f"DEBUG: Tool '{function_name}' executed. Output: {tool_output}")

                                try:
                                    # Correct and robust way to send tool feedback to Gemini
                                    final_response_from_gemini = chat_session.send_message(
                                        genai.protos.Part(
                                            function_response=genai.protos.FunctionResponse(
                                                name=function_name,
                                                response={ "output": str(tool_output) }
                                            )
                                        )
                                    )
                                    if final_response_from_gemini.text:
                                        speak(final_response_from_gemini.text)
                                        conversation_history.append(
                                            {"role": "model", "parts": [{"text": final_response_from_gemini.text}]})
                                    else:
                                        speak("I've executed the command and retrieved the information, but I don't have a specific verbal update beyond that.")
                                        conversation_history.append(
                                            {"role": "model",
                                             "parts": [{"text": "Tool executed, no specific verbal update from LLM."}]})

                                except Exception as feedback_error:
                                    print(
                                        f"DEBUG: Error sending tool feedback to Gemini (suppressed): {feedback_error}")
                                    speak(f"I've completed your request.")
                                    conversation_history.append({"role": "model", "parts": [
                                        {"text": f"Tool executed, but issue with Gemini feedback: {feedback_error}"}]})

                            except Exception as tool_execution_error:
                                error_message = f"There was an error while performing your request. Please check the terminal for details."
                                speak(error_message)
                                print(f"Error executing tool '{function_name}': {tool_execution_error}")
                                conversation_history.append({"role": "model", "parts": [
                                    {"text": f"Tool execution failed: {tool_execution_error}"}]})

                        else:
                            speak(f"Sorry, I don't have a function called {function_name}.")
                            print(f"DEBUG: Gemini requested unknown function: {function_name}")
                            conversation_history.append({"role": "model", "parts": [
                                {"text": f"Gemini requested unknown function: {function_name}"}]})

                    elif part.text and not tool_executed_this_turn:
                        speak(part.text)
                        print(f"DEBUG: Gemini's direct text response: {part.text}")
                        conversation_history.append({"role": "model", "parts": [{"text": part.text}]})

                if not tool_executed_this_turn and not any(
                        part.text for part in response.candidates[0].content.parts if hasattr(part, 'text')):
                    speak("I'm not sure how to respond to that. Can you try rephrasing?")
                    print("DEBUG: Gemini response had candidates but no tool call or direct text.")


            else:
                speak("I'm not sure how to respond to that. Can you try rephrasing?")
                print("DEBUG: Gemini response had no candidates or parts with content.")


        except GoogleAPIError as api_error:
            speak(
                "I'm sorry, I'm having trouble connecting to my brain (Gemini API) right now. Please check your network or API key.")
            print(f"Gemini API Error: {api_error}")
            conversation_history.append({"role": "model", "parts": [{"text": f"API Error: {api_error}"}]})
        except Exception as e:
            speak("I encountered an unexpected issue while processing your request. Can you please rephrase that?")
            print(f"General LLM processing error: {e}")
            conversation_history.append({"role": "model", "parts": [{"text": f"General Error: {e}"}]})
