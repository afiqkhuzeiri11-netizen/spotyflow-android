const ISO_DURATION_RE = /PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?/;

function parseIsoDuration(iso) {
  if (!iso) return 0;
  const m = ISO_DURATION_RE.exec(iso);
  if (!m) return 0;
  return Number(m[1] || 0) * 3600 + Number(m[2] || 0) * 60 + Number(m[3] || 0);
}

function stableId(str) {
  let h = 2166136261;
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return 100000000 + (Math.abs(h) % 800000000);
}

export default async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  if (req.method === 'OPTIONS') return res.status(204).end();

  const q = String(req.query.q || '').trim();
  const filter = String(req.query.filter || 'all').toLowerCase();
  const page = Math.max(1, Number(req.query.page || 1));
  const limit = Math.min(50, Math.max(1, Number(req.query.limit || 20)));
  const pageToken = req.query.page_token ? String(req.query.page_token) : '';

  if (!q) {
    return res.status(200).json({
      items: [],
      total: 0,
      page,
      limit,
      has_more: false,
      next_page_token: null,
      source: 'empty',
    });
  }

  const apiKey = (process.env.YOUTUBE_API_KEY || '').trim();
  if (!apiKey) {
    return res.status(503).json({ detail: 'YOUTUBE_API_KEY belum dikonfigurasi di Vercel.' });
  }

  try {
    const params = new URLSearchParams({
      part: 'snippet',
      q,
      maxResults: '50',
      regionCode: 'ID',
      relevanceLanguage: 'id',
      type: filter === 'channel' ? 'channel' : 'video',
      key: apiKey,
    });
    if (pageToken) params.set('pageToken', pageToken);
    if (filter === 'live') params.set('eventType', 'live');
    else if (filter === 'today') {
      params.set('publishedAfter', new Date(Date.now() - 24 * 3600 * 1000).toISOString());
    } else if (filter === 'most_viewed') params.set('order', 'viewCount');
    else if (filter === 'long') params.set('videoDuration', 'long');
    else if (filter === 'shorts') params.set('videoDuration', 'short');

    const ytHeaders = { Referer: 'https://spotyflow.vercel.app/' };
    const searchResp = await fetch(
      `https://www.googleapis.com/youtube/v3/search?${params.toString()}`,
      { headers: ytHeaders }
    );
    if (!searchResp.ok) {
      const errText = await searchResp.text();
      return res.status(502).json({ detail: `YouTube API error (${searchResp.status})`, raw: errText.slice(0, 200) });
    }
    const searchData = await searchResp.json();
    const rawItems = Array.isArray(searchData.items) ? searchData.items : [];
    const nextToken = searchData.nextPageToken || null;

    const videoIds = rawItems
      .map((it) => it?.id?.videoId)
      .filter((id) => typeof id === 'string' && id.length > 0);

    const detailsMap = new Map();
    if (videoIds.length > 0) {
      const detParams = new URLSearchParams({
        part: 'contentDetails,statistics,liveStreamingDetails',
        id: videoIds.slice(0, 50).join(','),
        key: apiKey,
      });
      const detResp = await fetch(
        `https://www.googleapis.com/youtube/v3/videos?${detParams.toString()}`,
        { headers: ytHeaders }
      );
      if (detResp.ok) {
        const detData = await detResp.json();
        for (const v of detData.items || []) {
          detailsMap.set(v.id, v);
        }
      }
    }

    const mapped = [];
    for (const item of rawItems) {
      const idObj = item.id || {};
      const sn = item.snippet || {};
      const thumbs = sn.thumbnails || {};
      const thumbUrl = thumbs.high?.url || thumbs.medium?.url || thumbs.default?.url || '';

      if (idObj.kind === 'youtube#channel' || (idObj.channelId && !idObj.videoId)) {
        const chId = idObj.channelId;
        if (!chId) continue;
        mapped.push({
          id: stableId(chId),
          yt_id: null,
          yt_channel_id: chId,
          result_type: 'channel',
          title: sn.title || 'Channel YouTube',
          description: sn.description || '',
          category: 'youtube',
          tags: ['channel', 'youtube'],
          thumbnail_url: thumbUrl,
          hls_url: null,
          mp4_url: '',
          duration_sec: 0,
          views: 0,
          likes_count: 0,
          is_short: false,
          is_live: false,
          processing_status: 'ready',
          created_at: sn.publishedAt || new Date().toISOString(),
          channel: {
            id: stableId(chId),
            username: chId,
            display_name: sn.channelTitle || sn.title || 'Channel',
            handle: `@${String(sn.title || 'channel').toLowerCase().replace(/[^a-z0-9_]/g, '').slice(0, 20) || 'channel'}`,
            avatar_url: thumbUrl,
            verified: true,
            subscribers_count: 0,
            subscribed_by_me: false,
            yt_channel_id: chId,
          },
        });
        continue;
      }

      const ytId = idObj.videoId;
      if (!ytId) continue;
      const det = detailsMap.get(ytId) || {};
      const durSec = parseIsoDuration(det.contentDetails?.duration);
      const stats = det.statistics || {};
      const isLive =
        sn.liveBroadcastContent === 'live' || Boolean(det.liveStreamingDetails?.concurrentViewers);
      const isShort = !isLive && durSec > 0 && durSec <= 62;
      const chId = sn.channelId || ytId;
      const chTitle = sn.channelTitle || 'YouTube Creator';

      mapped.push({
        id: stableId(ytId),
        yt_id: ytId,
        yt_channel_id: chId,
        result_type: isShort ? 'short' : 'video',
        embed_url: `https://www.youtube.com/embed/${ytId}?autoplay=1&mute=0&playsinline=1&rel=0&enablejsapi=1`,
        title: sn.title || 'Video YouTube',
        description: sn.description || '',
        category: isLive ? 'live' : 'youtube',
        tags: isShort ? ['youtube', 'shorts'] : ['youtube'],
        thumbnail_url: thumbUrl || `https://i.ytimg.com/vi/${ytId}/hqdefault.jpg`,
        hls_url: null,
        mp4_url: `https://www.youtube.com/watch?v=${ytId}`,
        duration_sec: isLive ? 0 : durSec,
        views: Number(stats.viewCount || 0),
        likes_count: Number(stats.likeCount || 0),
        is_short: isShort,
        is_live: isLive,
        processing_status: 'ready',
        created_at: sn.publishedAt || new Date().toISOString(),
        channel: {
          id: stableId(chId),
          username: chId,
          display_name: chTitle,
          handle: `@${chTitle.toLowerCase().replace(/[^a-z0-9_]/g, '').slice(0, 20) || 'creator'}`,
          avatar_url: null,
          verified: true,
          subscribers_count: 0,
          subscribed_by_me: false,
          yt_channel_id: chId,
        },
      });
    }

    const start = pageToken ? 0 : Math.max(0, (page - 1) * limit);
    const sliced = mapped.slice(start, start + limit);
    const hasMore = start + sliced.length < mapped.length || Boolean(nextToken);

    res.setHeader('Cache-Control', 'public, s-maxage=1800, stale-while-revalidate=3600');
    return res.status(200).json({
      items: sliced,
      total: mapped.length,
      page,
      limit,
      has_more: hasMore,
      next_page_token: start + sliced.length >= mapped.length ? nextToken : null,
      source: 'youtube_api',
    });
  } catch (err) {
    return res.status(500).json({ detail: err?.message || 'Gagal mencari video YouTube.' });
  }
}
