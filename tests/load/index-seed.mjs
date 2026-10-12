// Populate only the isolated local index from seeded DB rows. This is NOT an event-pipeline test.
import fs from 'node:fs';
import assert from 'node:assert/strict';
const rows = JSON.parse(fs.readFileSync('tests/load/generated/index-documents.json'));
for (let start = 0; start < rows.length; start += 500) {
  const body = rows.slice(start, start + 500).flatMap(row => [JSON.stringify({index:{_index:'products',_id:String(row.productId)}}),JSON.stringify(row)]).join('\n')+'\n';
  const response = await fetch('http://elasticsearch:9200/_bulk?refresh=wait_for', {method:'POST', headers:{'Content-Type':'application/x-ndjson'},body});
  assert.ok(response.ok);
  assert.equal((await response.json()).errors,false);
}
console.log(`Indexed ${rows.length} local fixture documents`);
