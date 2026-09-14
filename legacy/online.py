import requests
import wikipedia
import pywhatkit as kit
from email.message import EmailMessage
import smtplib
from decouple import config
import yfinance as yf
import json
from datetime import datetime
import webbrowser
from ddgs import DDGS # CHANGED: from duckduckgo_search import DDGS to from ddgs import DDGS


# --- Your Email and App Password Configuration --- (will have to replace to allow unviersal acess when user inptus theire secifcs)
EMAIL =
PASSWORD =


def find_my_ip():
    """Fetches the public IP address of the device."""
    try:
        ip_address = requests.get('https://api.ipify.org?format=json').json()
        return ip_address['ip']
    except requests.exceptions.RequestException as e:
        print(f"Error fetching IP address: {e}")
        return "N/A"


def search_on_wikipedia(query):
    """Searches Wikipedia for the given query and returns a summary."""
    try:
        results = wikipedia.summary(query, sentences=2)
        return results
    except wikipedia.exceptions.PageError:
        return "Sorry, I couldn't find any information on that on Wikipedia."
    except wikipedia.exceptions.DisambiguationError as e:
        options_str = ", ".join(e.options[:5])
        return f"Please be more specific. Your query could refer to: {options_str}."
    except Exception as e:
        print(f"Error during Wikipedia search: {e}")
        return "Sorry, an error occurred while searching Wikipedia."


def search_on_google(query):
    """Performs a Google search using pywhatkit and opens the web browser.
       Note: This function *opens* the browser. Use 'perform_web_search' for LLM-readable results.
    """
    try:
        kit.search(query)
        return "I have opened Google with your search query in your web browser."
    except Exception as e:
        print(f"Error opening Google search: {e}")
        return "Sorry, I couldn't open Google for your search."


def youtube(video):
    """Plays a video on YouTube using pywhatkit."""
    try:
        kit.playonyt(video)
        return f"Playing {video} on YouTube."
    except Exception as e:
        print(f"Error playing YouTube video: {e}")
        return "Sorry, I couldn't play that video on YouTube."


def send_email(receiver_add, subject, message):
    """
    Sends an email using Gmail's SMTP server.
    Requires App Password if 2-Step Verification is enabled on Gmail.
    """
    try:
        email = EmailMessage()
        email['To'] = receiver_add
        email['Subject'] = subject
        email['From'] = EMAIL
        email.set_content(message)

        s = smtplib.SMTP('smtp.gmail.com', 587)
        s.starttls()
        s.login(EMAIL, PASSWORD)
        s.send_message(email)
        s.quit()
        return True

    except smtplib.SMTPAuthenticationError as auth_error:
        print(f"SMTP Authentication Error: {auth_error}")
        print("Please check your email and App Password. If you have 2-Step Verification enabled,")
        print("you MUST use an App Password, not your regular Gmail password.")
        return False
    except Exception as e:
        print(f"Error sending email: {e}")
        return False


def get_stock_price(ticker_symbol):
    """
    Fetches the current stock price for a given company's ticker symbol using yfinance.
    Args:
        ticker_symbol (str): The stock ticker symbol (e.g., 'AAPL' for Apple, 'GOOGL' for Google).
    Returns:
        str: A string describing the current price, or an error message.
    """
    try:
        stock = yf.Ticker(ticker_symbol.upper())
        info = stock.info

        current_price = info.get('currentPrice')
        previous_close = info.get('previousClose')
        long_name = info.get('longName', ticker_symbol.upper())
        currency = info.get('currency', 'USD')

        if current_price:
            return f"The current price of {long_name} ({ticker_symbol.upper()}) is {current_price} {currency}."
        elif previous_close:
            return f"The last closing price of {long_name} ({ticker_symbol.upper()}) was {previous_close} {currency}."
        else:
            return f"Could not find current price information for {ticker_symbol.upper()}. Please check the ticker symbol and try again."

    except Exception as e:
        print(f"Error fetching stock price for {ticker_symbol}: {e}")
        return f"Sorry, I couldn't fetch the stock price for {ticker_symbol.upper()}. It might be an invalid symbol, or there's a network issue."


def load_credit_card_data(file_path='cards.json'):
    """Loads credit card data from a JSON file."""
    try:
        with open(file_path, 'r') as f:
            return json.load(f)
    except FileNotFoundError:
        print(f"Error: {file_path} not found. Credit card recommendations won't work.")
        return []
    except json.JSONDecodeError:
        print(f"Error: Invalid JSON in {file_path}. Please check its format.")
        return []
    except Exception as e:
        print(f"An unexpected error occurred while loading credit card data: {e}")
        return []


def get_best_credit_card_for_category(category_query):
    """
    Recommends the best credit card for a given spending category based on loaded data.
    """
    cards_data = load_credit_card_data()
    if not cards_data:
        return "I don't have any credit card data loaded to make recommendations. Please create a 'cards.json' file."

    category_map = {
        "groceries": "groceries",
        "food": "groceries",
        "supermarket": "groceries",
        "gas": "gas",
        "fuel": "gas",
        "restaurant": "restaurants",
        "dining": "restaurants",
        "eat": "restaurants",
        "cafe": "restaurants",
        "amazon": "amazon",
        "online shopping": "online shopping",
        "whole foods": "whole_foods",
        "travel": "travel",
        "hotel": "travel",
        "flight": "travel",
        "all other": "all_other",
        "anything": "all_other",
        "everything": "all_other",
        "bills": "utilities",
        "utilities": "utilities"
    }

    mapped_category = category_map.get(category_query.lower(), "all_other")

    best_card_info = None
    max_multiplier = 0.0

    current_month = datetime.now().month
    current_quarter_num = (current_month - 1) // 3 + 1
    q_key = f"Q{current_quarter_num}"

    for card in cards_data:
        current_card_multiplier = 0.0
        card_name = card.get('name', 'An unnamed card')

        if "rotating" in card.get('categories', {}):
            if q_key in card['categories']['rotating']:
                rotating_cat_info = card['categories']['rotating'][q_key]
                if rotating_cat_info['category'] == mapped_category:
                    current_card_multiplier = max(current_card_multiplier, rotating_cat_info['multiplier'])

        for cat_info in card.get('categories', {}).get('always', []):
            if cat_info['category'] == mapped_category:
                current_card_multiplier = max(current_card_multiplier, cat_info['multiplier'])
            elif cat_info['category'] == "all_other":
                current_card_multiplier = max(current_card_multiplier, cat_info['multiplier'])

        if current_card_multiplier > max_multiplier:
            max_multiplier = current_card_multiplier
            best_card_info = {
                "name": card_name,
                "multiplier": max_multiplier,
                "category": mapped_category,
                "type": card.get('type', 'Unknown Reward')
            }
        elif current_card_multiplier == max_multiplier and best_card_info:
            if card_name not in best_card_info['name']: # Append if multiple cards have same max
                best_card_info['name'] += f" or {card_name}"


    if best_card_info and max_multiplier > 0:
        if best_card_info['type'] == 'cashback':
            return f"For {best_card_info['category']}, I recommend using your {best_card_info['name']}. It offers {best_card_info['multiplier'] * 100:.0f}% cashback."
        elif best_card_info['type'] == 'points':
            return f"For {best_card_info['category']}, I recommend using your {best_card_info['name']}. It offers {best_card_info['multiplier']:.1f}x points per dollar."
        else:
            return f"For {best_card_info['category']}, I recommend using your {best_card_info['name']}. It offers a {best_card_info['multiplier']:.1f}x reward."
    else:
        general_spend_card = None
        for card in cards_data:
            for cat_info in card.get('categories', {}).get('always', []):
                if cat_info['category'] == "all_other" and cat_info['multiplier'] >= 0.01:
                    general_spend_card = card.get('name')
                    break
            if general_spend_card:
                break

        if general_spend_card:
            return f"I couldn't find a specific bonus for {category_query} on your cards. You might want to use your {general_spend_card} for general spending."
        else:
            return f"I couldn't find a specific recommendation for {category_query} based on your loaded cards."


def open_email_client():
    """Opens the user's default email client or Gmail in a web browser."""
    try:
        webbrowser.open("https://mail.google.com/")
        return "I have opened your Gmail in the browser."
    except Exception as e:
        print(f"Error opening email client: {e}")
        return "Sorry, I couldn't open your email client."

def perform_web_search(query, num_results=3):
    """
    Performs a web search using DuckDuckGo and returns a summary of results.
    This function is designed to be called by the LLM.
    """
    try:
        # CORRECTED: Removed 'keywords=' from the call
        results = DDGS().text(query, max_results=num_results)

        if not results:
            return "No relevant web search results found."

        search_summary = f"Here are some top results for '{query}':\n"
        for i, res in enumerate(results):
            title = res.get('title', 'No Title')
            href = res.get('href', '#')
            body = res.get('body', 'No snippet available.')
            search_summary += f"Result {i+1}: {title}\nURL: {href}\nSnippet: {body}\n\n"

        return search_summary

    except Exception as e:
        print(f"Error during web search: {e}")
        return f"Sorry, I encountered an error while trying to search the web: {e}"

def open_website(url):
    """Opens a specified URL in the default web browser."""
    try:
        webbrowser.open(url)
        return f"I have opened {url} in your browser."
    except Exception as e:
        print(f"Error opening URL {url}: {e}")
        return "Sorry, I couldn't open {url}."
