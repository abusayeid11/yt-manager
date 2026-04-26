"use client";

import { useState, FormEvent } from "react";

interface TranscriptSnippet {
  startTime: string;
  start: number;
  text: string;
}

interface TranscriptSegment {
  startTime: string;
  start: number;
  end: number;
  text: string;
}

export default function Home() {
  const [url, setUrl] = useState("");
  const [transcript, setTranscript] = useState("");
  const [snippets, setSnippets] = useState<TranscriptSnippet[]>([]);
  const [segments, setSegments] = useState<TranscriptSegment[]>([]);
  const [segmentDuration, setSegmentDuration] = useState(60);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!url.trim()) return;

    setLoading(true);
    setError("");
    setTranscript("");
    setSnippets([]);
    setSegments([]);

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
    } catch (err) {
      setError("Could not fetch transcript. Make sure the backend is running.");
    } finally {
      setLoading(false);
    }
  };

  const applySegmentation = (duration?: number) => {
    if (snippets.length === 0) return;

    const dur = duration ?? segmentDuration;
    const newSegments: TranscriptSegment[] = [];
    let currentStart = 0;
    let currentText = "";
    let windowEnd = dur;

    for (const snippet of snippets) {
      const startSec = snippet.start;

      if (startSec >= windowEnd && currentText) {
        const lastPunctuation = Math.max(
          currentText.lastIndexOf("."),
          currentText.lastIndexOf("!"),
          currentText.lastIndexOf("?")
        );

        if (lastPunctuation > currentText.length * 0.3) {
          const endIdx = lastPunctuation + 1;
          newSegments.push({
            startTime: formatTime(currentStart),
            start: currentStart,
            end: windowEnd,
            text: currentText.substring(0, endIdx).trim()
          });
          currentText = currentText.substring(endIdx).trim();
        } else {
          newSegments.push({
            startTime: formatTime(currentStart),
            start: currentStart,
            end: windowEnd,
            text: currentText.trim()
          });
          currentText = "";
        }

        currentStart = windowEnd;
        windowEnd = currentStart + dur;
      }

      if (currentText) currentText += " ";
      currentText += snippet.text;

      if (startSec >= windowEnd + dur * 0.8) {
        newSegments.push({
          startTime: formatTime(currentStart),
          start: currentStart,
          end: windowEnd,
          text: currentText.trim()
        });
        currentText = "";
        currentStart = windowEnd;
        windowEnd = currentStart + dur;
      }
    }

    if (currentText) {
      newSegments.push({
        startTime: formatTime(currentStart),
        start: currentStart,
        end: windowEnd,
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
    const text = segments.length > 0
      ? segments.map(s => `${s.startTime}\n${s.text}`).join("\n\n")
      : transcript;
    navigator.clipboard.writeText(text);
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
            className="flex-1 px-4 py-3 rounded-lg border border-zinc-300 focus:outline-none focus:ring-2 focus:ring-blue-500"
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

        {(transcript || segments.length > 0) && (
          <div className="bg-white rounded-lg shadow-md p-6">
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
                  className="px-2 py-1 text-sm rounded border border-zinc-300 focus:outline-none focus:ring-1 focus:ring-blue-500"
                >
                  <option value={30}>30s</option>
                  <option value={60}>60s</option>
                  <option value={90}>90s</option>
                </select>
              </div>
              <button
                onClick={copyToClipboard}
                className="px-3 py-1 text-sm text-zinc-600 hover:text-zinc-900"
              >
                Copy All
              </button>
            </div>

            <div className="space-y-6">
              {segments.length > 0 ? (
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