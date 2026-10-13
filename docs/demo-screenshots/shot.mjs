// Auto-screenshot the running app for the defense deck.
// Auth is injected via localStorage (web/src/lib/auth.js keys), so we never
// have to fight the antd login form.
import pw from 'file:///C:/Users/Melody/.workbuddy/binaries/node/workspace/node_modules/playwright/index.js';
const { chromium } = pw;

const TOKEN = process.env.WD_TOKEN;
const USER = process.env.WD_USER || 'shot1791787033';
const OUT = 'D:/SoftwareEngineering/FlagCamp/sf-dispatch-delivery/docs/demo-screenshots';

const browser = await chromium.launch({ channel: 'chrome' });
const ctx = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 2,
    locale: 'en-US',
});
await ctx.addInitScript(([t, u]) => {
    localStorage.setItem('token', t);
    localStorage.setItem('wd_username', u);
    localStorage.setItem('wd_role', 'USER');
}, [TOKEN, USER]);

const page = await ctx.newPage();
page.on('console', (m) => {
    if (m.type() === 'error') console.log('CONSOLE_ERR:', m.text().slice(0, 200));
});
page.on('pageerror', (e) => console.log('PAGE_ERR:', String(e).slice(0, 200)));

async function shot(url, name, wait = 3000) {
    await page
        .goto('http://localhost:3000' + url, { waitUntil: 'networkidle', timeout: 40000 })
        .catch((e) => console.log('goto ' + name + ':', e.message));
    await page.waitForTimeout(wait);
    await page.screenshot({ path: `${OUT}/${name}.png` });
    console.log('OK ->', name, '| url now:', page.url());
}

await shot('/orders', '03-orders');
await shot('/track?code=XPPXMNCZJY6YBLSW', '04-tracking', 5000);
await shot('/order/new', '02-wizard', 4000);

console.log('--- /order/new visible text ---');
console.log((await page.locator('body').innerText()).slice(0, 1500));

await browser.close();
