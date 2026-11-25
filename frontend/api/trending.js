const FALLBACK_TRENDING = [
  'review gadget terbaru 2026',
  'musik akustik santai indonesia',
  'live gaming valorant indonesia',
  'tutorial react fastapi fullstack',
  'resep ramen jepang rumahan',
  'podcast teknologi ai indonesia',
  'lofi chill beats untuk belajar',
  'vlog jalan jalan jepang 4k',
  'setup meja kerja minimalis',
  'highlight sepak bola terbaru',
];

export default async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  if (req.method === 'OPTIONS') return res.status(204).end();

  const apiKey = (process.env.YOUTUBE_API_KEY || '').trim();
  if (apiKey) {
    try {
      const resp = await fetch(
        `https://www.googleapis.com/youtube/v3/videos?part=snippet&chart=mostPopular&regionCode=ID&maxResults=10&key=${encodeURIComponent(apiKey)}`,
        { headers: { Referer: 'https://spotyflow.vercel.app/' } }
      );
      if (resp.ok) {
        const data = await resp.json();
        const titles = (data.items || [])
          .map((i) => String(i?.snippet?.title || '').trim())
          .filter(Boolean)
          .slice(0, 10);
        if (titles.length > 0) {
          res.setHeader('Cache-Control', 'public, s-maxage=1800, stale-while-revalidate=3600');
          return res.status(200).json({ trending: titles });
        }
      }
    } catch {
      // Fallback below
    }
  }

  return res.status(200).json({ trending: FALLBACK_TRENDING });
}
