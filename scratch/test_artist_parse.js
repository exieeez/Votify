async function testSearchRuns() {
  const ytmBody = {
    query: 'violent',
    context: {
      client: {
        clientName: 'WEB_REMIX',
        clientVersion: '1.20241126.01.00',
        hl: 'ru',
        gl: 'RU',
      },
    },
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
    for (const item of items.slice(0, 5)) {
      const r = item.musicResponsiveListItemRenderer;
      if (!r) continue;
      const title = r.flexColumns?.[0]?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.map(x => x.text).join('');
      const col1Runs = r.flexColumns?.[1]?.musicResponsiveListItemFlexColumnRenderer?.text?.runs;
      console.log(`\nTitle: "${title}"`);
      console.log('flexColumns[1] runs:', JSON.stringify(col1Runs, null, 2));
    }
  }
}

testSearchRuns();
