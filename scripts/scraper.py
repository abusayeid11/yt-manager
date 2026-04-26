import sys
import re
import json
from youtube_transcript_api import YouTubeTranscriptApi
from youtube_transcript_api._errors import TranscriptsDisabled, NoTranscriptFound


def extract_video_id(input_str: str) -> str:
    patterns = [
        r'(?:youtube\.com/watch\?v=)([a-zA-Z0-9_-]{11})',
        r'(?:youtu\.be/)([a-zA-Z0-9_-]{11})',
        r'(?:youtube\.com/embed/)([a-zA-Z0-9_-]{11})',
        r'^([a-zA-Z0-9_-]{11})$',
    ]
    for pattern in patterns:
        match = re.search(pattern, input_str)
        if match:
            return match.group(1)
    raise ValueError("Invalid YouTube URL or Video ID")


def get_transcript_json(input_str: str) -> str:
    video_id = extract_video_id(input_str)

    try:
        api = YouTubeTranscriptApi()
        transcript = api.fetch(video_id=video_id)
    except TranscriptsDisabled:
        print("ERROR: Transcripts are disabled for this video", file=sys.stderr)
        sys.exit(1)
    except NoTranscriptFound:
        print("ERROR: Transcripts are disabled for this video", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"ERROR: {str(e)}", file=sys.stderr)
        sys.exit(1)

    snippets = []
    for snippet in transcript:
        snippets.append({
            "text": snippet.text,
            "start": snippet.start,
            "duration": snippet.duration
        })

    result = {
        "videoId": video_id,
        "snippets": snippets
    }
    return json.dumps(result)


def main():
    if len(sys.argv) != 2:
        print("Usage: python scraper.py <YouTube_URL_or_Video_ID>", file=sys.stderr)
        sys.exit(1)

    input_str = sys.argv[1]
    transcript_json = get_transcript_json(input_str)
    print(transcript_json)


if __name__ == "__main__":
    main()