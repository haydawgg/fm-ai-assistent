const baseUrl = (process.env.FM_AI_SMOKE_URL || 'http://127.0.0.1:8080').replace(/\/$/, '');
const routes = ['', 'desk', 'players', 'chat', 'academy', 'contracts', 'first-xi', 'moneyball',
  'shortlist', 'compare-squads', 'squad-trim'];

async function get(pathname) {
  const response = await fetch(`${baseUrl}/${pathname}`, {
    redirect: 'manual',
    signal: AbortSignal.timeout(10_000),
  });
  const body = await response.text();
  if (!response.ok || body.length === 0) {
    throw new Error(`${pathname || '/'} returned HTTP ${response.status}`);
  }
  return { status: response.status, body };
}

const health = await get('actuator/health');
const healthPayload = JSON.parse(health.body);
if (healthPayload.status !== 'UP') {
  throw new Error(`backend health is ${healthPayload.status || 'unknown'}`);
}

for (const route of routes) {
  await get(route);
}

console.log(`Desktop smoke passed: health + ${routes.length} routes at ${baseUrl}`);
