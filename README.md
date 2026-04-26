# YouTube Transcript Manager

A full-stack application for fetching YouTube video transcripts.

## Project Structure

```
yt-manager/
├── backend-api/          # Spring Boot API (port 8080)
├── frontend-ui/          # Next.js frontend (port 3000)
└── scripts/              # Python scraper
```

## Prerequisites

- Java 21+
- Maven 3.8+
- Node.js 18+
- Python 3.9+

## Quick Start

### Backend
```bash
cd backend-api
mvn spring-boot:run
```

### Frontend
```bash
cd frontend-ui
npm install
npm run dev
```

## Environment Variables

Create `.env` from `.env.example` if needed:
- `PROJECT_ROOT` - Path to project root (for Python scraper)
- `PYTHON_EXEC` - Python executable path (optional)