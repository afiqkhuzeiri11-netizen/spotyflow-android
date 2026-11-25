export default async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  if (req.method === 'OPTIONS') return res.status(204).end();

  const q = String(req.query.q || '').trim();
  if (!q) {
    return res.status(200).json({ query: '', suggestions: [] });
  }

  try {
    const url = `http://suggestqueries.google.com/complete/search?client=firefox&ds=yt&hl=id&gl=ID&q=${encodeURIComponent(q)}`;
    const resp = await fetch(url);
    const payload = await resp.json();
    const list =
      Array.isArray(payload) && Array.isArray(payload[1])
        ? payload[1].map((s) => String(s).trim()).filter(Boolean).slice(0, 10)
        : [];
    res.setHeader('Cache-Control', 'public, s-maxage=3600, stale-while-revalidate=7200');
    return res.status(200).json({ query: q, suggestions: list });
  } catch {
    return res.status(200).json({ query: q, suggestions: [] });
  }
}
