# Context-Aware Enterprise Knowledge Engine

A full-stack, portfolio-grade knowledge platform that ingests documents, chunks their text, stores vector embeddings in PostgreSQL with pgvector, and answers grounded enterprise questions using the Gemini API. The project was built as a modular monolith foundation that keeps the repository runnable while preserving a migration-friendly architecture.

## Overview

This repository combines:

- a React frontend for chat, document upload, telemetry, and source inspection
- a Spring Boot backend for APIs, ingestion orchestration, and retrieval
- PostgreSQL with pgvector for metadata and vector similarity search
- Gemini-based generation for grounded Q&A
- asynchronous document jobs with WebSocket telemetry
- content-hash synchronization to avoid unnecessary re-indexing

The application is designed to support a practical knowledge workflow:

1. A document is uploaded.
2. It is parsed and chunked.
3. Each chunk gets an embedding.
4. Relevant chunks are retrieved using cosine similarity.
5. A grounded answer is generated from those chunks.
6. Citations link the answer back to the exact source chunk.

## Architecture

```text
+-------------------+        +-------------------------+
| React Frontend    | ----> | Spring Boot API         |
| Vite + React      |       | /api/v1/documents       |
| Chat / Docs /     |       | /api/v1/query           |
| Telemetry /       |       | /api/v1/jobs/{id}       |
| Citations         |       +------------+------------+
+-------------------+                    |
                                          v
                              +-------------------------+
                              | Ingestion Pipeline      |
                              | DocumentProcessing      |
                              | EmbeddingService        |
                              | IngestionService        |
                              +------------+------------+
                                           |
                                           v
                              +-------------------------+
                              | PostgreSQL + pgvector   |
                              | - documents             |
                              | - document_chunks       |
                              | - ingestion_jobs        |
                              +------------+------------+
                                           |
                                           v
                              +-------------------------+
                              | Gemini API              |
                              | generateContent         |
                              +-------------------------+
```

## Technology Stack

- Frontend: React 19 + Vite + JavaScript
- Backend: Java 17 + Spring Boot 3.3.x
- Data layer: PostgreSQL 16 + pgvector extension
- Persistence: Spring Data JPA + Hibernate
- AI: Google Gemini API (embedding / generation flow)
- Real-time updates: Spring WebSocket + STOMP + SockJS
- Document parsing: Apache PDFBox
- Containerization: Docker + Docker Compose
- Legacy preservation: original Python scripts kept under `legacy/`

## RAG Pipeline

The core retrieval workflow implemented in the application is:

1. User asks a question through the React chat interface.
2. The backend receives the question on `POST /api/v1/query`.
3. The query is embedded using the configured embedding strategy.
4. The system retrieves the top relevant chunks from `document_chunks` using a pgvector cosine similarity query.
5. The retrieved text is combined into a grounded prompt that instructs the model to answer using only those chunks.
6. Gemini generates the final answer, and the response includes source citations.
7. The frontend displays the answer and interactive citation badges.

## Asynchronous Ingestion

Uploads are processed in a non-blocking job loop:

- `POST /api/v1/documents` creates a job immediately.
- The job moves through states such as `PENDING`, `PARSING`, `CHUNKING`, `EMBEDDING`, `INDEXING`, `COMPLETE`, and `FAILED`.
- The backend updates status as the pipeline progresses.
- The frontend polls job status and also receives live telemetry via WebSocket.

## Content-Hash Synchronization

Before indexing a document, the application computes a SHA-256 hash of the file contents.

- If the hash already exists, the system skips expensive re-indexing and returns the existing synced record.
- If the file changed or is new, it replaces stale entries and processes the updated content.
- This keeps the knowledge base idempotent and reduces unnecessary database work.

## WebSocket Telemetry

The backend exposes STOMP over WebSocket endpoints to push ingestion status events.

- Topic: `/topic/jobs/{jobId}`
- Global topic: `/topic/ingestion`
- Messages broadcast job ID, document ID, stage, status, and timestamps.

This supports a live job monitor in the frontend without forcing page reloads.

## Citation Mapping

The retrieval flow returns structured citations that include:

- document filename
- chunk index
- section metadata
- snippet text

These are surfaced in the UI as clickable badges and can be inspected in the Source Citations panel for the exact retrieved evidence.

## Repository Layout

```text
.
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   ├── mvnw
│   └── src/
│       ├── main/java/com/knowledgeengine/
│       └── main/resources/application.yml
├── frontend/
│   ├── Dockerfile
│   ├── package.json
│   ├── vite.config.js
│   └── src/
├── docker/
│   └── postgres/initdb/01-init-vector.sql
├── legacy/
│   └── original Python assistant files preserved for reference
├── .env.example
├── .gitignore
├── docker-compose.yml
├── README.md
└── ...
```

## Local Development

### 1. Environment configuration

Copy the example environment file and fill in values as needed:

```bash
cp .env.example .env
```

Set the Gemini key if you want live generation calls:

```env
GEMINI_API_KEY=your_key_here
```

### 2. Run with Docker Compose

```bash
docker compose up --build
```

This starts:

- PostgreSQL with pgvector on `localhost:5432`
- Spring Boot API on `localhost:8080`
- Vite React app on `localhost:5173`

### 3. Run backend directly

```bash
cd backend
./mvnw clean test
```

### 4. Run frontend directly

```bash
cd frontend
npm install
npm run build
```

For local Vite development:

```bash
npm run dev -- --host 0.0.0.0
```

## API Surface

Key backend endpoints include:

- `GET /api/v1/health` — health check
- `POST /api/v1/documents` — upload and enqueue a document for ingestion
- `GET /api/v1/jobs/{jobId}` — inspect async job status
- `POST /api/v1/query` — ask a question against the indexed knowledge base

## Data and Storage

The application stores:

- document metadata in a `documents` table
- text chunks and embeddings in a `document_chunks` table with `vector(768)`
- job lifecycle state in an `ingestion_jobs` table

The pgvector extension is initialized with:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

## Security Notes

- Environment files are ignored by git.
- API keys should live only in `.env` or in the deployment environment.
- The project intentionally keeps the legacy Python assistant code under `legacy/` for historical reference and does not use it in the active app flow.

## Final Notes

This project is best understood as a clean, modular foundation for a context-aware enterprise knowledge engine. It demonstrates the major building blocks of a document ingestion and retrieval system while keeping the implementation intentionally maintainable and migration-friendly. The architecture is structured so later phases can evolve toward more advanced indexing, authentication, or distributed services without rewriting the core design from scratch.
