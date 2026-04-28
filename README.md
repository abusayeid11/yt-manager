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

**Note:** For semantic segmentation, obtain a Gemini API key from [Google AI Studio](https://aistudio.google.com/) and add it to `scripts/.env`:
```
GEMINI_API_KEY=your_api_key_here
```

If `GEMINI_API_KEY` is not set, semantic segmentation will return empty results.

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
| POST | `/api/v1/transcript/semantic` | Get AI-generated semantic segments (requires Gemini API key) |

### Request

```json
{
  "url": "https://www.youtube.com/watch?v=VIDEO_ID",
  "segmentationType": "TIMESTAMP",
  "segmentDuration": 60
}
```

**Request Fields:**
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `url` | string | Yes | YouTube URL or video ID |
| `segmentationType` | string | No | Set to `"TIMESTAMP"` to enable segmentation |
| `segmentDuration` | integer | No | Segment length in seconds (`30`, `60`, or `90`). Default: `60` |

### Response

```json
{
  "videoId": "VIDEO_ID",
  "rawText": "Full transcript text...",
  "snippets": [
    {"startTime": "0:00", "start": 0.0, "text": "Hello everyone..."},
    {"startTime": "0:05", "start": 5.0, "text": "Welcome to this video..."}
  ],
  "segments": [
    {"startTime": "0:00", "start": 0.0, "end": 60.0, "text": "Hello everyone... Welcome to this video..."}
  ]
}
```

**Response Fields:**
| Field | Type | Description |
|-------|------|-------------|
| `videoId` | string | YouTube video ID |
| `rawText` | string | Full transcript as plain text |
| `snippets` | array | Individual transcript segments with timestamps (always included) |
| `segments` | array | Grouped segments (included when `segmentationType` is `"TIMESTAMP"`) |

**Snippet Fields:**
| Field | Type | Description |
|-------|------|-------------|
| `startTime` | string | Formatted time (e.g., `"0:05"`) |
| `start` | double | Start time in seconds |
| `text` | string | Transcript text |

**Segment Fields:**
| Field | Type | Description |
|-------|------|-------------|
| `startTime` | string | Formatted start time |
| `start` | double | Start time in seconds |
| `end` | double | End time in seconds |
| `text` | string | Grouped transcript text |

### Semantic Segmentation Endpoint

```json
POST /api/v1/transcript/semantic
{
  "url": "https://www.youtube.com/watch?v=VIDEO_ID"
}
```

**Request Fields:**
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `url` | string | Yes | YouTube URL or video ID |

**Response:**
```json
{
  "videoId": "VIDEO_ID",
  "segments": [
    {
      "startTime": "0:00",
      "start": 0.0,
      "end": 120.5,
      "text": "Hello everyone... Welcome to this video...",
      "title": "Introduction"
    },
    {
      "startTime": "2:00",
      "start": 120.5,
      "end": 300.0,
      "text": "Today we're discussing...",
      "title": "Main Topic"
    }
  ]
}
```

**Response Fields:**
| Field | Type | Description |
|-------|------|-------------|
| `videoId` | string | YouTube video ID |
| `segments` | array | AI-generated semantic segments with titles |

**Semantic Segment Fields:**
| Field | Type | Description |
|-------|------|-------------|
| `startTime` | string | Formatted start time |
| `start` | double | Start time in seconds |
| `end` | double | End time in seconds |
| `text` | string | Grouped transcript text |
| `title` | string | AI-generated chapter title |

**Note:** If `GEMINI_API_KEY` is not set or invalid, `segments` will be empty.

## Tech Stack

- **Backend**: Spring Boot 3.4, Java 21
- **Frontend**: Next.js 16, React 19, Tailwind CSS 4
- **Scraper**: Python, youtube-transcript-api, google-generativeai