"""Text chunker for the DravyaSetu RAG layer."""


def chunk_text(text: str, size: int = 800, overlap: int = 100) -> list[str]:
    """Split text into ~size-character chunks with overlap.

    Prefers to cut at a paragraph or sentence end inside the last 40% of the
    window, so chunks rarely stop mid-sentence.
    """
    text = text.replace("\r\n", "\n").strip()
    chunks = []
    start, n = 0, len(text)

    while start < n:
        end = min(start + size, n)
        if end < n:
            window = text[start:end]
            cut = max(window.rfind("\n\n"), window.rfind(".\n"), window.rfind(". "))
            if cut > size * 0.6:
                end = start + cut + 1
        piece = text[start:end].strip()
        if piece:
            chunks.append(piece)
        if end >= n:
            break
        start = max(end - overlap, start + 1)

    return chunks