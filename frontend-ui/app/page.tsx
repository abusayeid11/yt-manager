"use client";

import { useState, FormEvent } from "react";

interface TranscriptSnippet {
  startTime: string;
  start: number;
  duration?: number;
  text: string;
}

interface TranscriptSegment {
  startTime: string;
  start: number;
  end: number;
  text: string;
}

interface SemanticSegment {
  startTime: string;
  start: number;
  end: number;
  text: string;
  title: string;
}

interface VideoMetadata {
  videoId: string;
  title: string;
  channelName: string;
  channelUrl: string;
  thumbnailUrl: string;
}

const SENTENCE_END = /[.!?]+\s*$/;

const isSentenceEnd = (text: string): boolean => {
  if (!text) return false;
  const trimmed = text.trim();
  return SENTENCE_END.test(trimmed) ||
         trimmed.endsWith(".") ||
         trimmed.endsWith("!") ||
         trimmed.endsWith("?");
};

export default function Home() {
  const [url, setUrl] = useState("");
  const [transcript, setTranscript] = useState("");
  const [snippets, setSnippets] = useState<TranscriptSnippet[]>([]);
  const [segments, setSegments] = useState<TranscriptSegment[]>([]);
  const [semanticSegments, setSemanticSegments] = useState<SemanticSegment[]>([]);
  const [videoMetadata, setVideoMetadata] = useState<VideoMetadata | null>(null);
  const [segmentDuration, setSegmentDuration] = useState(60);
  const [segmentType, setSegmentType] = useState<"basic" | "semantic">("basic");
  const [loading, setLoading] = useState(false);
  const [semanticLoading, setSemanticLoading] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!url.trim()) return;

    setLoading(true);
    setError("");
    setTranscript("");
    setSnippets([]);
    setSegments([]);
    setSemanticSegments([]);
    setVideoMetadata(null);
    setSegmentType("basic");

    try {
      const response = await fetch("/api/v1/transcript", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          url,
          segmentationType: "TIMESTAMP",
          segmentDuration: segmentDuration
        }),
      });

      if (!response.ok) {
        throw new Error("Failed to fetch transcript");
      }

      const data = await response.json();
      setTranscript(data.rawText || "");
      setSnippets(data.snippets || []);
      setSegments(data.segments || []);
      setVideoMetadata(data.metadata || null);
    } catch (err) {
      setError("Could not fetch transcript. Make sure the backend is running.");
    } finally {
      setLoading(false);
    }
  };

  const fetchSemanticSegments = async () => {
    if (!url.trim()) return;

    setSemanticLoading(true);
    try {
      const response = await fetch("/api/v1/transcript/semantic", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ url }),
      });

      if (!response.ok) {
        throw new Error("Failed to fetch semantic segments");
      }

      const data = await response.json();
      setSemanticSegments(data.segments || []);
      setSegmentType("semantic");
    } catch (err) {
      setError("Failed to generate semantic segmentation. Check GEMINI_API_KEY is set.");
    } finally {
      setSemanticLoading(false);
    }
  };

  const applySegmentation = (duration?: number) => {
    if (snippets.length === 0) return;

    const dur = duration ?? segmentDuration;
    const newSegments: TranscriptSegment[] = [];
    let currentStart = 0;
    let currentText = "";
    let windowEnd = dur;
    let textEnd = dur;
    let lastEndedWithSentence = false;

    for (const snippet of snippets) {
      const startSec = snippet.start;
      const snippetDuration = snippet.duration || 0;

      if (startSec >= windowEnd && currentText) {
        const extensionLimit = windowEnd + dur * 0.5;
        if (lastEndedWithSentence && startSec < extensionLimit) {
        } else {
          newSegments.push({
            startTime: formatTime(currentStart),
            start: currentStart,
            end: textEnd,
            text: currentText.trim()
          });

          currentStart = windowEnd;
          windowEnd = currentStart + dur;
          textEnd = windowEnd;
          currentText = "";
          lastEndedWithSentence = false;
        }
      }

      if (currentText) currentText += " ";
      currentText += snippet.text;
      textEnd = startSec + snippetDuration;
      lastEndedWithSentence = isSentenceEnd(snippet.text);

      const forcedSplitThreshold = currentStart + dur * 0.8;
      if (startSec >= forcedSplitThreshold && currentText) {
        newSegments.push({
          startTime: formatTime(currentStart),
          start: currentStart,
          end: textEnd,
          text: currentText.trim()
        });

        currentStart = windowEnd;
        windowEnd = currentStart + dur;
        textEnd = windowEnd;
        currentText = "";
        lastEndedWithSentence = false;
      }

      while (startSec >= windowEnd && currentText) {
        newSegments.push({
          startTime: formatTime(currentStart),
          start: currentStart,
          end: textEnd,
          text: currentText.trim()
        });

        currentStart = windowEnd;
        windowEnd = currentStart + dur;
        textEnd = windowEnd;
        currentText = "";
        lastEndedWithSentence = false;
      }
    }

    if (currentText) {
      newSegments.push({
        startTime: formatTime(currentStart),
        start: currentStart,
        end: textEnd,
        text: currentText.trim()
      });
    }

    setSegments(newSegments);
  };

  const formatTime = (seconds: number): string => {
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs.toString().padStart(2, "0")}`;
  };

  const copyToClipboard = () => {
    if (segmentType === "semantic" && semanticSegments.length > 0) {
      const text = semanticSegments.map(s => `${s.startTime} - ${s.title}\n${s.text}`).join("\n\n");
      navigator.clipboard.writeText(text);
    } else {
      const text = segments.length > 0
        ? segments.map(s => `${s.startTime}\n${s.text}`).join("\n\n")
        : transcript;
      navigator.clipboard.writeText(text);
    }
  };

  return (
    <main className="min-h-screen bg-zinc-50 flex flex-col items-center justify-center p-8">
      <div className="w-full max-w-2xl">
        <h1 className="text-3xl font-bold text-center mb-8 text-zinc-800">
          YouTube Transcript Fetcher
        </h1>

        <form onSubmit={handleSubmit} className="flex gap-3 mb-8">
          <input
            type="text"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="Enter YouTube URL or Video ID"
            className="flex-1 px-4 py-3 text-black rounded-lg border border-zinc-300 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <button
            type="submit"
            disabled={loading || !url.trim()}
            className="px-6 py-3 bg-blue-600 text-white font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loading ? "Loading..." : "Fetch Transcript"}
          </button>
        </form>

        {error && (
          <p className="text-red-600 text-center mb-4">{error}</p>
        )}

        {(transcript || segments.length > 0 || semanticSegments.length > 0) && (
          <div className="bg-white rounded-lg shadow-md p-6">
            {videoMetadata && (
              <div className="flex items-start gap-4 mb-6 pb-4 border-b border-zinc-200">
                {videoMetadata.thumbnailUrl && (
                  <img 
                    src={videoMetadata.thumbnailUrl} 
                    alt={videoMetadata.title}
                    className="w-40 h-24 object-cover rounded"
                  />
                )}
                <div className="flex-1">
                  <h2 className="text-lg font-semibold text-zinc-800 line-clamp-2">
                    {videoMetadata.title}
                  </h2>
                  {videoMetadata.channelName && (
                    <a 
                      href={videoMetadata.channelUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-sm text-blue-600 hover:underline"
                    >
                      {videoMetadata.channelName}
                    </a>
                  )}
                </div>
              </div>
            )}

            <div className="flex justify-between items-center mb-4">
              <div className="flex items-center gap-2">
                <span className="text-sm text-zinc-500">Segment:</span>
                <select
                  value={segmentDuration}
                  onChange={(e) => {
                    const newDuration = Number(e.target.value);
                    setSegmentDuration(newDuration);
                    if (snippets.length > 0) applySegmentation(newDuration);
                  }}
                  className="px-2 py-1 text-sm rounded border border-zinc-300 focus:outline-none focus:ring-1 focus:ring-blue-500 text-black"
                >
                  <option value={30}>30s</option>
                  <option value={60}>60s</option>
                  <option value={90}>90s</option>
                </select>
                <div className="flex gap-1 ml-2">
                  <button
                    onClick={() => setSegmentType("basic")}
                    className={`px-3 py-1 text-sm rounded border ${
                      segmentType === "basic"
                        ? "bg-blue-600 text-white border-blue-600"
                        : "bg-white text-zinc-600 border-zinc-300 hover:bg-zinc-50"
                    }`}
                  >
                    Basic
                  </button>
                  <button
                    onClick={fetchSemanticSegments}
                    disabled={semanticLoading || !url.trim()}
                    className={`px-3 py-1 text-sm rounded border ${
                      segmentType === "semantic"
                        ? "bg-blue-600 text-white border-blue-600"
                        : "bg-white text-zinc-600 border-zinc-300 hover:bg-zinc-50 disabled:opacity-50"
                    }`}
                  >
                    {semanticLoading ? "AI..." : "Semantic"}
                  </button>
                </div>
              </div>
              <button
                onClick={copyToClipboard}
                className="px-3 py-1 text-sm text-zinc-600 hover:text-zinc-900"
              >
                Copy All
              </button>
            </div>

            <div className="space-y-6">
              {segmentType === "semantic" && semanticSegments.length > 0 ? (
                semanticSegments.map((segment, index) => (
                  <section key={index}>
                    <div className="flex items-center gap-2">
                      <span className="text-blue-600 font-medium">[{segment.startTime}]</span>
                      <span className="bg-purple-100 text-purple-700 px-2 py-0.5 rounded text-sm font-medium">
                        {segment.title}
                      </span>
                    </div>
                    <p className="text-zinc-700 mt-1 leading-7">{segment.text}</p>
                  </section>
                ))
              ) : segments.length > 0 ? (
                segments.map((segment, index) => (
                  <section key={index}>
                    <span className="text-blue-600 font-medium">[{segment.startTime}]</span>
                    <p className="text-zinc-700 mt-1 leading-7">{segment.text}</p>
                  </section>
                ))
              ) : (
                <p className="text-zinc-700 leading-7">{transcript}</p>
              )}
            </div>
          </div>
        )}
      </div>
    </main>
  );
}