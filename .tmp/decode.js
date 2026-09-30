const fs = require('fs');
const zlib = require('zlib');

const src = 'C:/Users/PC/.dsh/sessions/--C-Users-PC-IdeaProjects-english_app_cdcntt--/ed1335f8-fe9b-4136-b478-043fb5767499/session.v3.jsonl.zstd';
const out = 'C:/Users/PC/IdeaProjects/english_app_cdcntt/.tmp/sess.jsonl';

const chunks = [];
const rs = fs.createReadStream(src);
const zs = zlib.createZstdDecompress();
const ws = fs.createWriteStream(out);

zs.on('data', (d) => chunks.push(d));
zs.on('error', (e) => { console.log('ZSTD_ERR', e.message); });
ws.on('finish', () => {
  const buf = Buffer.concat(chunks);
  console.log('decompressed bytes', buf.length);
  const lines = buf.toString('utf8').split('\n').filter((l) => l.trim());
  console.log('lines', lines.length);
  for (const l of lines) {
    let o;
    try { o = JSON.parse(l); } catch { continue; }
    const role = o.role || o.type;
    if (role === 'user') {
      const t = typeof o.content === 'string' ? o.content : JSON.stringify(o.content);
      console.log('=== USER MSG ===');
      console.log(t.slice(0, 6000));
    }
  }
});
rs.pipe(zs).pipe(ws);
