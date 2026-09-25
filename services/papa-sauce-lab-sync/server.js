import express from 'express';
import cors from 'cors';
import pg from 'pg';

const { Pool } = pg;
const app = express();
const port = Number(process.env.PORT || 10000);
const syncKey = process.env.PSL_SYNC_KEY || '';
const databaseUrl = process.env.DATABASE_URL || '';

if (!databaseUrl) throw new Error('DATABASE_URL is required');

const pool = new Pool({
  connectionString: databaseUrl,
  ssl: databaseUrl.includes('localhost') ? false : { rejectUnauthorized: false }
});

app.use(cors({ origin: true, credentials: false }));
app.use(express.json({ limit: '12mb' }));

const STORES = new Set(['transactions','stock_movements','snapshots','corrections','periods']);

async function init() {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS psl_records (
      store_name TEXT NOT NULL,
      record_key TEXT NOT NULL,
      payload JSONB NOT NULL,
      client_updated_at BIGINT NOT NULL DEFAULT 0,
      server_updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      PRIMARY KEY (store_name, record_key)
    );
    CREATE INDEX IF NOT EXISTS psl_records_server_updated_idx
      ON psl_records (server_updated_at);
  `);
}

function auth(req, res, next) {
  if (!syncKey) return res.status(503).json({ error: 'sync_not_configured' });
  const key = req.get('x-psl-sync-key') || '';
  if (key !== syncKey) return res.status(401).json({ error: 'unauthorized' });
  next();
}

app.get('/health', async (_req, res) => {
  try {
    await pool.query('SELECT 1');
    res.json({ service: 'PAPA_SAUCE_LAB_SYNC', status: 'ONLINE', version: 1 });
  } catch (e) {
    res.status(500).json({ service: 'PAPA_SAUCE_LAB_SYNC', status: 'DB_ERROR' });
  }
});

app.post('/sync/push', auth, async (req, res) => {
  const records = Array.isArray(req.body?.records) ? req.body.records : [];
  if (records.length > 10000) return res.status(413).json({ error: 'too_many_records' });

  const client = await pool.connect();
  let accepted = 0;
  try {
    await client.query('BEGIN');
    for (const rec of records) {
      const store = String(rec?.store || '');
      const key = String(rec?.key || '');
      const payload = rec?.payload;
      const ts = Number(rec?.ts || 0);
      if (!STORES.has(store) || !key || !payload || typeof payload !== 'object') continue;

      const q = await client.query(`
        INSERT INTO psl_records(store_name, record_key, payload, client_updated_at, server_updated_at)
        VALUES ($1,$2,$3::jsonb,$4,NOW())
        ON CONFLICT (store_name, record_key) DO UPDATE
          SET payload = EXCLUDED.payload,
              client_updated_at = EXCLUDED.client_updated_at,
              server_updated_at = NOW()
        WHERE EXCLUDED.client_updated_at >= psl_records.client_updated_at
        RETURNING record_key
      `, [store, key, JSON.stringify(payload), ts]);
      if (q.rowCount) accepted++;
    }
    await client.query('COMMIT');
    res.json({ ok: true, accepted });
  } catch (e) {
    await client.query('ROLLBACK');
    res.status(500).json({ error: 'push_failed' });
  } finally {
    client.release();
  }
});

app.get('/sync/pull', auth, async (_req, res) => {
  try {
    const q = await pool.query(`
      SELECT store_name, record_key, payload, client_updated_at
      FROM psl_records
      ORDER BY store_name, record_key
    `);
    res.json({
      ok: true,
      records: q.rows.map(r => ({
        store: r.store_name,
        key: r.record_key,
        payload: r.payload,
        ts: Number(r.client_updated_at || 0)
      }))
    });
  } catch (e) {
    res.status(500).json({ error: 'pull_failed' });
  }
});

app.get('/sync/stats', auth, async (_req, res) => {
  const q = await pool.query(`
    SELECT store_name, COUNT(*)::int AS count
    FROM psl_records
    GROUP BY store_name
    ORDER BY store_name
  `);
  res.json({ ok: true, stores: q.rows });
});

await init();
app.listen(port, '0.0.0.0', () => {
  console.log('Papa Sauce Lab Sync listening on', port);
});
