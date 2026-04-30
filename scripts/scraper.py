import sys
import re
import json
import os
from dotenv import load_dotenv
from youtube_transcript_api import YouTubeTranscriptApi
from youtube_transcript_api._errors import TranscriptsDisabled, NoTranscriptFound, CouldNotRetrieveTranscript

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

    def probe_transcript_availability(self, video_id: str) -> dict:
        print(f"DEBUG: probe_transcript_availability START for {video_id}", file=sys.stderr)
        try:
            print("DEBUG: Creating YouTubeTranscriptApi", file=sys.stderr)
            api = YouTubeTranscriptApi()

            print("DEBUG: Calling api.list()", file=sys.stderr)
            transcript_list = api.list(video_id=video_id)

            print("DEBUG: api.list() returned, converting to list", file=sys.stderr)
            available = list(transcript_list)

            print(f"DEBUG: Found {len(available)} available transcripts", file=sys.stderr)
            if not available:
                return {"available": False, "error": "NO_TRANSCRIPTS", "message": "No transcripts available for this video"}

            first_transcript = available[0]
            print(f"DEBUG: Selected transcript: {first_transcript.language} ({first_transcript.language_code})", file=sys.stderr)

            return {
                "available": True,
                "language": first_transcript.language,
                "language_code": first_transcript.language_code,
                "is_generated": first_transcript.is_generated,
                "can_translate": first_transcript.is_translatable,
                "transcript_object": first_transcript
            }

        except TranscriptsDisabled:
            print("DEBUG: EXCEPTION - TranscriptsDisabled", file=sys.stderr)
            return {"available": False, "error": "TRANSCRIPTS_DISABLED", "message": "Transcripts are disabled for this video"}
        except NoTranscriptFound:
            print("DEBUG: EXCEPTION - NoTranscriptFound", file=sys.stderr)
            return {"available": False, "error": "NO_TRANSCRIPT_FOUND", "message": "No transcript found for this video"}
        except CouldNotRetrieveTranscript as e:
            print(f"DEBUG: EXCEPTION - CouldNotRetrieveTranscript: {e}", file=sys.stderr)
            error_str = str(e).lower()
            if "video unavailable" in error_str or "private" in error_str:
                return {"available": False, "error": "VIDEO_UNAVAILABLE", "message": "Video is unavailable or private"}
            elif "not accessible" in error_str or "region" in error_str:
                return {"available": False, "error": "REGION_BLOCKED", "message": "Transcript not available in your region"}
            return {"available": False, "error": "COULD_NOT_RETRIEVE", "message": str(e)}
        except Exception as e:
            print(f"DEBUG: EXCEPTION - {type(e).__name__}: {e}", file=sys.stderr)
            return {"available": False, "error": "PROBE_FAILED", "message": f"Probe failed: {str(e)}"}

    def get_transcript(self, input_str: str) -> dict:
        print(f"DEBUG: get_transcript START", file=sys.stderr)
        video_id = self.extract_video_id(input_str)
        print(f"DEBUG: Extracted video_id: {video_id}", file=sys.stderr)

        print("DEBUG: Calling probe_transcript_availability", file=sys.stderr)
        probe_result = self.probe_transcript_availability(video_id)
        print("DEBUG: probe_transcript_availability COMPLETED", file=sys.stderr)

        if not probe_result["available"]:
            error = probe_result["error"]
            message = probe_result["message"]

            print(f"ERROR: {message}", file=sys.stderr)

            error_codes = {
                "VIDEO_UNAVAILABLE": 10,
                "TRANSCRIPTS_DISABLED": 1,
                "NO_TRANSCRIPT_FOUND": 2,
                "NO_TRANSCRIPTS": 2,
                "REGION_BLOCKED": 3,
                "COULD_NOT_RETRIEVE": 4,
                "PROBE_FAILED": 4
            }
            sys.exit(error_codes.get(error, 1))

        transcript_obj = probe_result["transcript_object"]

        print("DEBUG: Calling transcript_obj.fetch()", file=sys.stderr)
        try:
            transcript = transcript_obj.fetch()
            print("DEBUG: fetch() COMPLETED", file=sys.stderr)
        except Exception as e:
            print(f"ERROR during fetch: {str(e)}", file=sys.stderr)
            sys.exit(1)

        print(f"DEBUG: Processing {len(transcript)} snippets", file=sys.stderr)
        snippets = [
            {
                "text": snippet.text,
                "start": snippet.start,
                "duration": snippet.duration
            }
            for snippet in transcript
        ]
        print(f"DEBUG: Processed {len(snippets)} snippets, returning", file=sys.stderr)

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