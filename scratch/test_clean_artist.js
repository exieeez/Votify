function extractArtistFromRuns(runs, fallback) {
  if (!Array.isArray(runs) || runs.length === 0) return fallback || 'Unknown Artist';

  const firstText = runs[0]?.text?.trim();
  if (['Выпуск', 'Подкаст', 'Профиль', 'Плейлист'].includes(firstText)) {
    return null; // Skip non-track
  }

  const artistRun = runs.find(r => 
    r.navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType === 'MUSIC_PAGE_TYPE_ARTIST' ||
    r.navigationEndpoint?.browseEndpoint?.browseId?.startsWith('UC')
  );
  if (artistRun && artistRun.text?.trim()) {
    return artistRun.text.trim();
  }

  const filtered = runs
    .map(r => r.text?.trim())
    .filter(Boolean)
    .filter(t => !['•', ',', 'Композиция', 'Песня', 'Видео', 'Выпуск', 'Подкаст', 'Профиль', 'Плейлист', 'Альбом', 'Сингл', 'Song', 'Video', 'Track', 'Single', 'Album', 'Playlist'].includes(t))
    .filter(t => !/^\d+:\d+$/.test(t))
    .filter(t => !/\d+\s*(тыс|млн|млрд|k|m|b|views|просмотр)/i.test(t))
    .filter(t => !/^\d{4}$/.test(t))
    .filter(t => !/^\d+\s+[а-яёa-z]+(\s+\d{4})?/i.test(t));

  return filtered[0] || fallback || 'Unknown Artist';
}

async function test() {
  const ytmBody = {
    query: 'violent',
    context: {
      client: { clientName: 'WEB_REMIX', clientVersion: '1.20241126.01.00', hl: 'ru', gl: 'RU' }
    }
  };
  const res = await fetch('https://music.youtube.com/youtubei/v1/search?prettyPrint=false', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'User-Agent': 'Mozilla/5.0' },
    body: JSON.stringify(ytmBody),
  });
  const data = await res.json();
  const sections = data?.contents?.tabbedSearchResultsRenderer?.tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents || [];

  for (const sec of sections) {
    const items = sec.musicShelfRenderer?.contents || sec.itemSectionRenderer?.contents || [];
    for (const item of items) {
      const r = item.musicResponsiveListItemRenderer;
      if (!r) continue;
      const title = r.flexColumns?.[0]?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.map(x => x.text).join('');
      const col1Runs = r.flexColumns?.[1]?.musicResponsiveListItemFlexColumnRenderer?.text?.runs;
      const artist = extractArtistFromRuns(col1Runs);
      if (artist === null) {
        console.log(`[SKIPPED NON-TRACK] "${title}"`);
      } else {
        console.log(`[TRACK] "${title}" by "${artist}"`);
      }
    }
  }
}

test();
