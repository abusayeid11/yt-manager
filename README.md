# YouTube Transcript Manager

A full-stack application for fetching YouTube video transcripts.

## Project Structure

```
yt-manager/
├── backend-api/          # Spring Boot API
├── frontend-ui/          # Next.js frontend
└── scripts/              # Python scraper
```

## Prerequisites

- Java 21+
- Maven 3.8+
- Node.js 18+
- Python 3.9+ (for scraper)

## Setup

### Backend

```bash
cd backend-api
mvn spring-boot:run
```

The API will start on `http://localhost:8080`

### Frontend

```bash
cd frontend-ui
npm install
npm run dev
```

The frontend will start on `http://localhost:3000`

### Python Scraper

```bash
cd scripts
python -m venv venv
source venv/bin/activate  # Linux/Mac
# venv\Scripts\activate   # Windows
pip install -r requirements.txt
```

## Environment Variables

Copy `.env.example` to `.env` and configure as needed:

```
# Backend (optional - defaults shown)
SERVER_PORT=8080

# Frontend (optional)
NEXT_PUBLIC_API_URL=http://localhost:8080
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/transcript` | Get transcript for a YouTube video |

### Request

```json
{
  "url": "https://www.youtube.com/watch?v=VIDEO_ID"
}
```

### Response

```json
{
  "videoId": "VIDEO_ID",
  "transcript": "Full transcript text..."
}
```

## Tech Stack

- **Backend**: Spring Boot 3.4, Java 21
- **Frontend**: Next.js 16, React 19, Tailwind CSS 4
- **Scraper**: Python, youtube-transcript-api