import sys
import re
import json
import os
from dotenv import load_dotenv
from youtube_transcript_api import YouTubeTranscriptApi
from youtube_transcript_api._errors import TranscriptsDisabled, NoTranscriptFound

load_dotenv(os.path.join(os.path.dirname(__file__), '.env'))

try:
    import google.generativeai as genai
except ImportError:
    genai = None


class YouTubeScraper:
    VIDEO_ID_PATTERNS = [
        r'(?:youtube\.com/watch\?v=)([a-zA-Z0-9_-]{11})',
        r'(?:youtu\.be/)([a-zA-Z0-9_-]{11})',
        r'(?:youtube\.com/embed/)([a-zA-Z0-9_-]{11})',
        r'^([a-zA-Z0-9_-]{11})$',
    ]

    def extract_video_id(self, input_str: str) -> str:
        for pattern in self.VIDEO_ID_PATTERNS:
            match = re.search(pattern, input_str)
            if match:
                return match.group(1)
        raise ValueError("Invalid YouTube URL or Video ID")

    def get_transcript(self, input_str: str) -> dict:
        video_id = self.extract_video_id(input_str)

        try:
            api = YouTubeTranscriptApi()
            transcript = api.fetch(video_id=video_id)
        except TranscriptsDisabled:
            print("ERROR: Transcripts are disabled for this video", file=sys.stderr)
            sys.exit(1)
        except NoTranscriptFound:
            print("ERROR: No transcript available for this video", file=sys.stderr)
            sys.exit(2)
        except Exception as e:
            print(f"ERROR: {str(e)}", file=sys.stderr)
            sys.exit(1)

        snippets = [
            {
                "text": snippet.text,
                "start": snippet.start,
                "duration": snippet.duration
            }
            for snippet in transcript
        ]

        return {
            "videoId": video_id,
            "snippets": snippets
        }

    def get_semantic_boundaries(self, snippets: list) -> list:
        if genai is None:
            print("WARNING: google-generativeai not installed. Run: pip install google-generativeai", file=sys.stderr)
            return []

        api_key = os.environ.get("GEMINI_API_KEY")
        if not api_key:
            print("WARNING: GEMINI_API_KEY not set. Set env var to enable semantic segmentation.", file=sys.stderr)
            return []

        try:
            genai.configure(api_key=api_key)
            model = genai.GenerativeModel("gemini-2.5-flash")
        except Exception as e:
            print(f"WARNING: Failed to configure Gemini: {str(e)}", file=sys.stderr)
            return []

        formatted_snippets = []
        for i, snippet in enumerate(snippets):
            formatted_snippets.append(f"[{i}] {snippet['text']}")

        transcript_text = " ".join(formatted_snippets)

        prompt = f"""Analyze the following YouTube transcript and identify meaningful topic shifts. 
Return ONLY a valid JSON array of objects with 'start_index' and 'title' for each chapter. 
start_index is the 0-based index of the snippet where each chapter begins.
Keep titles concise (2-5 words). 
Example: [{{"start_index": 0, "title": "Introduction"}}, {{"start_index": 15, "title": "Main Topic"}}, {{"start_index": 45, "title": "Conclusion"}}]

Transcript:
{transcript_text}"""

        try:
            response = model.generate_content(prompt)
            response_text = response.text.strip()

            if response_text.startswith("```json"):
                response_text = response_text[7:]
            if response_text.startswith("```"):
                response_text = response_text[3:]
            if response_text.endswith("```"):
                response_text = response_text[:-3]

            boundaries = json.loads(response_text.strip())
            return boundaries
        except json.JSONDecodeError as e:
            print(f"WARNING: Failed to parse Gemini response: {str(e)}", file=sys.stderr)
            return []
        except Exception as e:
            print(f"WARNING: Gemini API error: {str(e)}", file=sys.stderr)
            return []


def main():
    if len(sys.argv) < 2:
        print("Usage: python scraper.py <YouTube_URL_or_Video_ID> [--semantic]", file=sys.stderr)
        sys.exit(1)

    use_semantic = "--semantic" in sys.argv

    scraper = YouTubeScraper()
    try:
        input_str = sys.argv[1]
        transcript = scraper.get_transcript(input_str)

        if use_semantic:
            chapters = scraper.get_semantic_boundaries(transcript["snippets"])
            transcript["chapters"] = chapters

        print(json.dumps(transcript))
    except ValueError as e:
        print(f"ERROR: {str(e)}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()