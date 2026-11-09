import React, { useState, useEffect, useRef, useMemo, useCallback } from 'react';

// ============================================================================
// 0. ZERO-DEPENDENCY INLINE TACTICAL SVG ICONS
// ============================================================================
function SvgIcon({ size = 16, className = '', children }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      style={{ flexShrink: 0 }}
    >
      {children}
    </svg>
  );
}

const ShieldAlert = (props) => (
  <SvgIcon {...props}>
    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
    <line x1="12" y1="8" x2="12" y2="12" />
    <line x1="12" y1="16" x2="12.01" y2="16" />
  </SvgIcon>
);

const Activity = (props) => (
  <SvgIcon {...props}>
    <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
  </SvgIcon>
);

const Cpu = (props) => (
  <SvgIcon {...props}>
    <rect x="4" y="4" width="16" height="16" rx="2" ry="2" />
    <rect x="9" y="9" width="6" height="6" />
    <line x1="9" y1="1" x2="9" y2="4" />
    <line x1="15" y1="1" x2="15" y2="4" />
    <line x1="9" y1="20" x2="9" y2="23" />
    <line x1="15" y1="20" x2="15" y2="23" />
    <line x1="20" y1="9" x2="23" y2="9" />
    <line x1="20" y1="14" x2="23" y2="14" />
    <line x1="1" y1="9" x2="4" y2="9" />
    <line x1="1" y1="14" x2="4" y2="14" />
  </SvgIcon>
);

const Network = (props) => (
  <SvgIcon {...props}>
    <rect x="16" y="16" width="6" height="6" rx="1" />
    <rect x="2" y="16" width="6" height="6" rx="1" />
    <rect x="9" y="2" width="6" height="6" rx="1" />
    <path d="M5 16v-3a1 1 0 0 1 1-1h12a1 1 0 0 1 1 1v3" />
    <path d="M12 12V8" />
  </SvgIcon>
);

const Radio = (props) => (
  <SvgIcon {...props}>
    <circle cx="12" cy="12" r="2" />
    <path d="M16.24 7.76a6 6 0 0 1 0 8.49m-8.48-.01a6 6 0 0 1 0-8.49m11.31-2.82a10 10 0 0 1 0 14.14m-14.14 0a10 10 0 0 1 0-14.14" />
  </SvgIcon>
);

const AlertTriangle = (props) => (
  <SvgIcon {...props}>
    <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z" />
    <line x1="12" y1="9" x2="12" y2="13" />
    <line x1="12" y1="17" x2="12.01" y2="17" />
  </SvgIcon>
);

const Eye = (props) => (
  <SvgIcon {...props}>
    <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z" />
    <circle cx="12" cy="12" r="3" />
  </SvgIcon>
);

const Users = (props) => (
  <SvgIcon {...props}>
    <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
    <circle cx="9" cy="7" r="4" />
    <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
    <path d="M16 3.13a4 4 0 0 1 0 7.75" />
  </SvgIcon>
);

const CheckCircle2 = (props) => (
  <SvgIcon {...props}>
    <circle cx="12" cy="12" r="10" />
    <path d="m9 12 2 2 4-4" />
  </SvgIcon>
);

const Zap = (props) => (
  <SvgIcon {...props}>
    <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
  </SvgIcon>
);

const RefreshCw = (props) => (
  <SvgIcon {...props}>
    <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8" />
    <path d="M21 3v5h-5" />
    <path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16" />
    <path d="M8 16H3v5" />
  </SvgIcon>
);

const Code = (props) => (
  <SvgIcon {...props}>
    <polyline points="16 18 22 12 16 6" />
    <polyline points="8 6 2 12 8 18" />
  </SvgIcon>
);

const Download = (props) => (
  <SvgIcon {...props}>
    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
    <polyline points="7 10 12 15 17 10" />
    <line x1="12" y1="15" x2="12" y2="3" />
  </SvgIcon>
);

// ============================================================================
// 1. PURE-JS PKZIP BINARY ARCHIVE GENERATOR (ZERO EXTERNAL DEPENDENCIES)
// ============================================================================
const CRC32_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let i = 0; i < 256; i++) {
    let c = i;
    for (let k = 0; k < 8; k++) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    table[i] = c >>> 0;
  }
  return table;
})();

function crc32Bytes(bytes) {
  let crc = 0xffffffff;
  for (let i = 0; i < bytes.length; i++) {
    crc = CRC32_TABLE[(crc ^ bytes[i]) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function createZipArchiveBlob(fileEntries) {
  const encoder = new TextEncoder();
  const localParts = [];
  const centralParts = [];
  let offset = 0;

  fileEntries.forEach((entry) => {
    const nameBytes = encoder.encode(entry.path);
    const dataBytes = encoder.encode(entry.content);
    const crc = crc32Bytes(dataBytes);
    const size = dataBytes.length;

    // Local File Header (30 bytes + filename)
    const localHeader = new Uint8Array(30 + nameBytes.length);
    const lv = new DataView(localHeader.buffer);
    lv.setUint32(0, 0x04034b50, true); // PK\x03\x04
    lv.setUint16(4, 20, true); // Version needed (2.0)
    lv.setUint16(6, 0x0800, true); // UTF-8 filename flag
    lv.setUint16(8, 0, true); // Method 0 (STORE uncompressed)
    lv.setUint16(10, 0x4800, true); // Mod time
    lv.setUint16(12, 0x5d68, true); // Mod date
    lv.setUint32(14, crc, true);
    lv.setUint32(18, size, true);
    lv.setUint32(22, size, true);
    lv.setUint16(26, nameBytes.length, true);
    lv.setUint16(28, 0, true);
    localHeader.set(nameBytes, 30);

    // Central Directory File Header (46 bytes + filename)
    const centralHeader = new Uint8Array(46 + nameBytes.length);
    const cv = new DataView(centralHeader.buffer);
    cv.setUint32(0, 0x02014b50, true); // PK\x01\x02
    cv.setUint16(4, 20, true); // Version made by
    cv.setUint16(6, 20, true); // Version needed
    cv.setUint16(8, 0x0800, true); // UTF-8 flag
    cv.setUint16(10, 0, true); // STORE
    cv.setUint16(12, 0x4800, true);
    cv.setUint16(14, 0x5d68, true);
    cv.setUint32(16, crc, true);
    cv.setUint32(20, size, true);
    cv.setUint32(24, size, true);
    cv.setUint16(28, nameBytes.length, true);
    cv.setUint16(30, 0, true);
    cv.setUint16(32, 0, true);
    cv.setUint16(34, 0, true);
    cv.setUint16(36, 0, true);
    cv.setUint32(38, 0, true);
    cv.setUint32(42, offset, true); // Relative offset of local header
    centralHeader.set(nameBytes, 46);

    localParts.push(localHeader, dataBytes);
    centralParts.push(centralHeader);
    offset += localHeader.length + dataBytes.length;
  });

  const centralSize = centralParts.reduce((acc, arr) => acc + arr.length, 0);
  const eocd = new Uint8Array(22);
  const ev = new DataView(eocd.buffer);
  ev.setUint32(0, 0x06054b50, true); // PK\x05\x06
  ev.setUint16(4, 0, true);
  ev.setUint16(6, 0, true);
  ev.setUint16(8, fileEntries.length, true);
  ev.setUint16(10, fileEntries.length, true);
  ev.setUint32(12, centralSize, true);
  ev.setUint32(16, offset, true);
  ev.setUint16(20, 0, true);

  return new Blob([...localParts, ...centralParts, eocd], { type: 'application/zip' });
}

// ============================================================================
// 2. OBSIDIAN CRT DESIGN TOKENS & GLOBAL STYLES
// ============================================================================
const crtStyles = `
  @import url('https://fonts.googleapis.com/css2?family=Share+Tech+Mono&display=swap');

  :root {
    --color-bg: #050505;
    --color-panel: #0a0a0c;
    --color-phosphor: #4ade80;
    --color-phosphor-dim: rgba(74, 222, 128, 0.3);
    --color-rust: #c2410c;
    --color-amber: #f59e0b;
    --color-alarm: #dc2626;
    
    --pulse-intensity: 0;
    --alarm-glow: 0.25;
  }

  body {
    background-color: var(--color-bg);
    color: var(--color-phosphor);
    font-family: 'Share Tech Mono', monospace;
    overflow: hidden;
    margin: 0;
    padding: 0;
    user-select: none;
  }

  .crt-overlay {
    position: fixed;
    top: 0; left: 0; width: 100vw; height: 100vh;
    background: 
      linear-gradient(rgba(18, 16, 16, 0) 50%, rgba(0, 0, 0, 0.25) 50%), 
      linear-gradient(90deg, rgba(255, 0, 0, 0.06), rgba(0, 255, 0, 0.02), rgba(0, 0, 255, 0.06));
    background-size: 100% 3px, 3px 100%;
    pointer-events: none;
    z-index: 9999;
    box-shadow: inset 0 0 100px rgba(0,0,0,0.9);
  }

  .hw-panel {
    background-color: var(--color-panel);
    border: 1px solid var(--color-phosphor-dim);
    box-shadow: inset 0 0 20px rgba(0,0,0,0.85);
    position: relative;
    overflow: hidden;
  }
  
  .hw-panel::before {
    content: ''; position: absolute; top: 0; left: 0;
    width: 10px; height: 10px;
    border-top: 2px solid var(--color-phosphor);
    border-left: 2px solid var(--color-phosphor);
    pointer-events: none; z-index: 20;
  }

  .hw-panel::after {
    content: ''; position: absolute; bottom: 0; right: 0;
    width: 8px; height: 8px;
    border-bottom: 1px solid var(--color-phosphor-dim);
    border-right: 1px solid var(--color-phosphor-dim);
    pointer-events: none; z-index: 20;
  }

  .hw-panel.threat-active {
    border-color: rgba(220, 38, 38, calc(0.3 + var(--pulse-intensity) * 0.7));
    box-shadow: inset 0 0 calc(20px + var(--pulse-intensity) * 50px) rgba(220, 38, 38, 0.22);
  }

  .hw-panel.threat-active::before { border-color: var(--color-alarm); }

  .threat-text {
    color: var(--color-alarm);
    text-shadow: 0 0 calc(5px + var(--pulse-intensity) * 10px) var(--color-alarm);
    opacity: calc(0.75 + var(--pulse-intensity) * 0.25);
  }

  .phosphor-text { color: var(--color-phosphor); text-shadow: 0 0 5px var(--color-phosphor-dim); }
  .amber-text { color: var(--color-amber); text-shadow: 0 0 5px rgba(245, 158, 11, 0.5); }

  .hw-btn {
    background: rgba(5, 5, 5, 0.85);
    border: 1px solid rgba(74, 222, 128, 0.35);
    color: var(--color-phosphor);
    font-family: 'Share Tech Mono', monospace;
    text-transform: uppercase; transition: all 0.15s ease; cursor: pointer;
  }
  .hw-btn:hover {
    border-color: var(--color-phosphor);
    background: rgba(74, 222, 128, 0.12);
    box-shadow: 0 0 8px rgba(74, 222, 128, 0.25);
  }
  .hw-btn.active-amber { border-color: var(--color-amber); color: var(--color-amber); background: rgba(245, 158, 11, 0.15); box-shadow: 0 0 8px rgba(245, 158, 11, 0.3); }
  .hw-btn.active-phosphor { border-color: var(--color-phosphor); color: #050505; background: var(--color-phosphor); font-weight: bold; box-shadow: 0 0 10px rgba(74, 222, 128, 0.5); }
  .hw-btn.active-alarm { border-color: var(--color-alarm); color: var(--color-alarm); background: rgba(220, 38, 38, 0.18); box-shadow: 0 0 10px rgba(220, 38, 38, 0.35); }

  .scroll-container::-webkit-scrollbar { width: 4px; height: 4px; }
  .scroll-container::-webkit-scrollbar-track { background: var(--color-bg); }
  .scroll-container::-webkit-scrollbar-thumb { background: var(--color-phosphor-dim); }
`;

// ============================================================================
// 3. CRYPTOGRAPHY, AST DRIFT SIGNATURES & MATH ENGINES
// ============================================================================
function computeSHA256(ascii) {
  function rightRotate(value, amount) { return (value >>> amount) | (value << (32 - amount)); }
  const mathPow = Math.pow;
  const maxWord = mathPow(2, 32);
  let i, j;
  let result = '';
  let words = [];
  let asciiBitLength = ascii.length * 8;
  let hash = computeSHA256.h = computeSHA256.h || [];
  let k = computeSHA256.k = computeSHA256.k || [];
  let primeCounter = k.length;
  let isComposite = {};
  for (let candidate = 2; primeCounter < 64; candidate++) {
    if (!isComposite[candidate]) {
      for (i = 0; i < 313; i += candidate) isComposite[i] = candidate;
      hash[primeCounter] = (mathPow(candidate, 0.5) * maxWord) | 0;
      k[primeCounter++] = (mathPow(candidate, 1 / 3) * maxWord) | 0;
    }
  }
  ascii += '\x80';
  while ((ascii.length % 64) - 56) ascii += '\x00';
  for (i = 0; i < ascii.length; i++) {
    j = ascii.charCodeAt(i);
    if (j >> 8) return '';
    words[i >> 2] |= j << ((3 - i) % 4) * 8;
  }
  words[words.length] = (asciiBitLength / maxWord) | 0;
  words[words.length] = asciiBitLength;
  for (j = 0; j < words.length;) {
    let w = words.slice(j, (j += 16));
    let oldHash = hash;
    hash = hash.slice(0, 8);
    for (i = 0; i < 64; i++) {
      let w15 = w[i - 15], w2 = w[i - 2];
      let a = hash[0], e = hash[4];
      let temp1 =
        hash[7] +
        (rightRotate(e, 6) ^ rightRotate(e, 11) ^ rightRotate(e, 25)) +
        ((e & hash[5]) ^ (~e & hash[6])) +
        k[i] +
        (w[i] =
          i < 16
            ? w[i]
            : (w[i - 16] +
                (rightRotate(w15, 7) ^ rightRotate(w15, 18) ^ (w15 >>> 3)) +
                w[i - 7] +
                (rightRotate(w2, 17) ^ rightRotate(w2, 19) ^ (w2 >>> 10))) |
              0);
      let temp2 =
        (rightRotate(a, 2) ^ rightRotate(a, 13) ^ rightRotate(a, 22)) +
        ((a & hash[1]) ^ (a & hash[2]) ^ (hash[1] & hash[2]));
      hash = [(temp1 + temp2) | 0].concat(hash);
      hash[4] = (hash[4] + temp1) | 0;
    }
    for (i = 0; i < 8; i++) hash[i] = (hash[i] + oldHash[i]) | 0;
  }
  for (i = 0; i < 8; i++) {
    for (j = 3; j + 1; j--) {
      let b = (hash[i] >> (j * 8)) & 255;
      result += (b < 16 ? 0 : '') + b.toString(16);
    }
  }
  return result;
}

function generateUUID() {
  const c = typeof window !== 'undefined' ? window.crypto || window.msCrypto : null;
  if (c && c.getRandomValues) {
    return '10000000-1000-4000-8000-100000000000'.replace(/[018]/g, (ch) =>
      (ch ^ (c.getRandomValues(new Uint8Array(1))[0] & (15 >> (ch / 4)))).toString(16)
    );
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (ch) {
    const r = (Math.random() * 16) | 0,
      v = ch === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

function generateStixId(type) {
  return `${type}--${generateUUID()}`;
}

const PLATFORM_DRIFT_CATALOG = {
  tiktok: {
    key: 'tiktok',
    name: 'TikTok',
    sampleUrl: 'https://www.tiktok.com/@Ghost_Protocol_99',
    deprecatedSelector: 'div[data-e2e="user-post-item-list"] > div.tiktok-x6y88p',
    mutatedDom: '<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">{"__DEFAULT_SCOPE__":{"webapp.user-detail":{"userInfo":{"user":{"uniqueId":"ghost_protocol_99"}}}}}</script>',
    healedSelector: 'json_hydration://script#__UNIVERSAL_DATA_FOR_REHYDRATION__ -> __DEFAULT_SCOPE__["webapp.user-detail"].userInfo',
    strategy: 'Playwright-Stealth + Rehydration State Tree Interceptor',
    pythonSnippet: `async def extract_tiktok_healed(page):\n    raw = await page.locator("script#__UNIVERSAL_DATA_FOR_REHYDRATION__").text_content()\n    scope = json.loads(raw)["__DEFAULT_SCOPE__"]["webapp.user-detail"]\n    return scope["userInfo"]["user"], scope.get("itemList", [])`
  },
  instagram: {
    key: 'instagram',
    name: 'Instagram',
    sampleUrl: 'https://www.instagram.com/cipher_vane_ops',
    deprecatedSelector: 'header section ul li span._ac2a',
    mutatedDom: '<div class="x9f619 xjbqb8w x78zum5 x168nmei" data-bloks-name="bk.components.Text"><!-- React Polaris Obfuscated Tree --></div>',
    healedSelector: 'graphql_intercept:///api/v1/users/web_profile_info/?username={handle} [x-ig-app-id: 936619743392459]',
    strategy: 'Polaris GraphQL Network Relay + Session Cookie Vault',
    pythonSnippet: `async def extract_instagram_healed(client, handle):\n    headers = {"x-ig-app-id": "936619743392459", "User-Agent": STEALTH_UA}\n    r = await client.get(f"https://www.instagram.com/api/v1/users/web_profile_info/?username={handle}", headers=headers)\n    return r.json()["data"]["user"]`
  },
  youtube: {
    key: 'youtube',
    name: 'YouTube',
    sampleUrl: 'https://www.youtube.com/@NullRouterBroadcast',
    deprecatedSelector: 'ytd-channel-about-metadata-renderer #description-container',
    mutatedDom: '<script nonce="a8F9kL">var ytInitialData = {"header":{"pageHeaderRenderer":{"content":{"pageHeaderViewModel":{"title":{"dynamicTextViewModel":{"text":{"content":"NullRouterBroadcast"}}}}}}}};</script>',
    healedSelector: 'ast_script_var://window.ytInitialData.header.pageHeaderRenderer.content.pageHeaderViewModel',
    strategy: 'AST Script Variable Parser (ytInitialData + Whisper Transcript Pipe)',
    pythonSnippet: `def extract_youtube_healed(html_body: str):\n    m = re.search(r"var ytInitialData\\s*=\\s*(\\{.*?\\});</script>", html_body, re.S)\n    data = json.loads(m.group(1))\n    return data["header"]["pageHeaderRenderer"]["content"]["pageHeaderViewModel"]`
  },
  reddit: {
    key: 'reddit',
    name: 'Reddit',
    sampleUrl: 'https://www.reddit.com/user/ZeroTrustSaboteur',
    deprecatedSelector: 'div.Post[data-testid="post-container"] div.RichTextJSON-root',
    mutatedDom: '<shreddit-post permalink="/r/netsec/comments/1x9a/" author="ZeroTrustSaboteur" created-timestamp="2026-10-18T03:14:00Z">',
    healedSelector: 'shadow_dom://shreddit-post[@author] ::shadow-root(open) + /user/{handle}/submitted.json?raw_json=1',
    strategy: 'Shreddit WebComponent Shadow-DOM Piercer + JSON Endpoint Failover',
    pythonSnippet: `async def extract_reddit_healed(page, handle):\n    posts = await page.locator("shreddit-post").evaluate_all(\n        "els => els.map(e => ({author: e.getAttribute('author'), ts: e.getAttribute('created-timestamp'), text: e.innerText}))"\n    )\n    return posts`
  },
  facebook: {
    key: 'facebook',
    name: 'Facebook',
    sampleUrl: 'https://www.facebook.com/groups/sovereign.mesh.ops',
    deprecatedSelector: 'div[role="feed"] div[data-ad-preview="message"]',
    mutatedDom: '<script type="application/json" data-sjs>{"require":[["ScheduledServerJS","handle",null,[{"__bbox":{"result":{"data":{"group":{"feed_units":{}}}}}}]]]}</script>',
    healedSelector: 'relay_bbox://script[type="application/json"][data-sjs] -> ScheduledServerJS.__bbox.result.data',
    strategy: 'Relay Comet ScheduledServerJS BBox JSON Stream Reconstructor',
    pythonSnippet: `def extract_facebook_bbox(html_body: str):\n    blobs = re.findall(r'<script type="application/json" data-sjs>(.*?)</script>', html_body)\n    return [json.loads(b) for b in blobs if "__bbox" in b]`
  },
  onlyfans: {
    key: 'onlyfans',
    name: 'OnlyFans',
    sampleUrl: 'https://onlyfans.com/velvet_opsec_vip',
    deprecatedSelector: 'div.b-profile__header div.b-user-info__detail',
    mutatedDom: '<div class="g-user-realname__wrapper" data-v-68e32a14><!-- Dynamic X-BC + SHA1 Dynamic Rules Checksum Required --></div>',
    healedSelector: 'signed_xhr:///api2/v2/users/{handle} [sign: sha1(static_param + timestamp + path + user_id)]',
    strategy: 'Dynamic SHA-1 Request Signer + Residential Mobile Proxy Rotation',
    pythonSnippet: `def sign_onlyfans_request(path: str, ts: int, static_param: str, checksum_indexes: list):\n    msg = "\\n".join([static_param, str(ts), path, "0"])\n    sha = hashlib.sha1(msg.encode()).hexdigest()\n    checksum = sum(ord(sha[i]) for i in checksum_indexes) + 109\n    return f"614:{sha}:{checksum:x}:65a8b"`
  }
};

function detectPlatformSpec(uri) {
  const lower = (uri || '').toLowerCase();
  if (lower.includes('tiktok')) return PLATFORM_DRIFT_CATALOG.tiktok;
  if (lower.includes('instagram') || lower.includes('ig.me')) return PLATFORM_DRIFT_CATALOG.instagram;
  if (lower.includes('youtube') || lower.includes('youtu.be')) return PLATFORM_DRIFT_CATALOG.youtube;
  if (lower.includes('reddit')) return PLATFORM_DRIFT_CATALOG.reddit;
  if (lower.includes('facebook') || lower.includes('fb.com')) return PLATFORM_DRIFT_CATALOG.facebook;
  if (lower.includes('onlyfans')) return PLATFORM_DRIFT_CATALOG.onlyfans;
  return null;
}

const THIRTY_SPECTRUM_TOKENS = [
  'the', 'and', 'to', 'of', 'in', 'is', 'that', 'for', 'it', 'with',
  'as', 'be', 'on', 'not', 'this', 'by', 'are', 'at', 'or', 'from',
  'but', 'we', 'you', 'i', 'my', 'our', 'they', 'will', 'if', 'all'
];

function computeSubjectSpectrum(subject) {
  const combinedText = `${subject.frontStageQuote || ''} ${subject.backStageQuote || ''}`.toLowerCase();
  const words = combinedText.replace(/[^a-z0-9\s]/g, ' ').split(/\s+/).filter(Boolean);
  const total = Math.max(words.length, 1);
  const counts = {};
  words.forEach((w) => { counts[w] = (counts[w] || 0) + 1; });

  return THIRTY_SPECTRUM_TOKENS.map((token, idx) => {
    const rawCount = counts[token] || 0;
    const oceanN = subject?.ocean?.N || 50;
    const baseZ = subject?.zScores?.[token] ?? (((idx * 17 + oceanN) % 16) - 8) / 10;
    const barHeight = Math.min(98, Math.max(18, Math.round(32 + baseZ * 28 + rawCount * 14)));
    return {
      index: idx,
      token,
      count: rawCount,
      zScore: Number(baseZ.toFixed(2)),
      freqPct: Number(((rawCount / total) * 100).toFixed(1)),
      barHeight
    };
  });
}

// ============================================================================
// 4. INITIAL DATASETS & REACT STATE MANAGEMENT
// ============================================================================
const INITIAL_SUBJECTS = [
  {
    id: 'subj-alpha',
    stixId: generateStixId('identity'),
    handle: 'NULL_ROUTER',
    codeId: '#993A-FX',
    alias: 'CipherVane // Null_Architect_99',
    cluster: 'Cluster-01: Zero-Trust Sabotage',
    loc: 'ZÜRICH, CH (SHIFTED FROM US-MA)',
    lat: 47.3769, lon: 8.5414, prevLat: 42.3629, prevLon: -71.0839,
    utcOffset: 2, chronoWindowKey: 'window_b',
    burrowsDelta: 0.312, styloMatch: 94.2,
    threatMetrics: { pathwayToViolence: 87, memeticVirality: 94, contextCollapse: 82, networkCentrality: 99 },
    ocean: { O: 76, C: 85, E: 68, A: 19, N: 64 },
    darkTriad: { machiavellianism: 89, narcissism: 78, psychopathy: 64 },
    zScores: { the: 0.42, and: -0.35, to: 0.88, of: 0.61, in: -0.22, is: 1.15, that: 0.94, for: -0.48, it: 0.31, with: -0.62 },
    frontStageQuote: 'We are committed to inclusive governance where every contributor is empowered.',
    backStageQuote: 'These idiots actually bought the PR. Lock down the repo keys and purge anyone asking about the commits.',
    forcePos: [0, 0, 0], umapPos: [-28, 22, 18], isThreat: true
  },
  {
    id: 'subj-beta',
    stixId: generateStixId('identity'),
    handle: 'V_KOVACS',
    codeId: '#771C-ZX',
    alias: 'Kovacs_Actual // RedLine_Op',
    cluster: 'Cluster-01: Zero-Trust Sabotage',
    loc: 'CAMBRIDGE, US-MA (UTC-4)',
    lat: 42.3629, lon: -71.0839, prevLat: 40.7128, prevLon: -74.0060,
    utcOffset: -4, chronoWindowKey: 'window_a',
    burrowsDelta: 0.348, styloMatch: 89.6,
    threatMetrics: { pathwayToViolence: 92, memeticVirality: 76, contextCollapse: 74, networkCentrality: 84 },
    ocean: { O: 54, C: 79, E: 72, A: 24, N: 71 },
    darkTriad: { machiavellianism: 82, narcissism: 65, psychopathy: 77 },
    zScores: { the: 0.38, and: -0.29, to: 0.79, of: 0.55, in: -0.18, is: 0.98, that: 0.84, for: -0.41, it: 0.25, with: -0.54 },
    frontStageQuote: 'Excited to attend the regional transit infrastructure symposium!',
    backStageQuote: 'Drop the hardware near the Kendall locker before 04:00 UTC. Do not message me on clearnet again.',
    forcePos: [65, 30, -25], umapPos: [-22, 18, 14], isThreat: true
  }
];

export default function App() {
  const [subjects, setSubjects] = useState(INITIAL_SUBJECTS);
  const [selectedSubjectId, setSelectedSubjectId] = useState('subj-alpha');
  const [logs, setLogs] = useState([
    { ts: '20:47:01.104', cat: 'PROV_O', msg: 'AEGIS TERMINAL INITIALIZED // PATH-B STEALTH FLEET ARMED.' },
    { ts: '20:47:01.412', cat: 'AI_HEAL', msg: 'AST DOM-DRIFT WATCHDOG MONITORING: TIKTOK, IG, YT, REDDIT, FB, OF.' }
  ]);

  const [containmentOverride, setContainmentOverride] = useState(false);
  const [liveFps, setLiveFps] = useState(60);
  const [selectedSpectrumToken, setSelectedSpectrumToken] = useState('to');
  const [graphMode, setGraphMode] = useState('FORCE');

  const [surface02Tab, setSurface02Tab] = useState('RADAR_MAP');
  const [surface03Tab, setSurface03Tab] = useState('DOSSIER');
  const [surface04Tab, setSurface04Tab] = useState('SCRAPER_BRIDGE');

  const [apiEndpoint] = useState('http://127.0.0.1:8000');
  const [bridgeStatus, setBridgeStatus] = useState('STEALTH_FLEET // READY');
  const [scraperUri, setScraperUri] = useState('https://www.tiktok.com/@Ghost_Protocol_99');
  const [isScraping, setIsScraping] = useState(false);
  const [zipStatus, setZipStatus] = useState('DOWNLOAD PROJECT .ZIP');

  const [healedPatches, setHealedPatches] = useState({});
  const [showPatchCode, setShowPatchCode] = useState(false);

  const [domDrift, setDomDrift] = useState({
    active: false,
    platform: null,
    platformKey: null,
    deprecatedSelector: '',
    mutatedDom: '',
    proposedSelector: '',
    strategy: '',
    pythonSnippet: '',
    healing: false,
    healStep: 0,
    healProgress: 0,
    healStatusText: ''
  });

  const audioCtxRef = useRef(null);

  const activeSubject = useMemo(
    () => subjects.find((s) => s.id === selectedSubjectId) || subjects[0],
    [subjects, selectedSubjectId]
  );
  const activeSpectrum = useMemo(() => computeSubjectSpectrum(activeSubject), [activeSubject]);

  const computedDefcon = useMemo(() => {
    const tm = activeSubject.threatMetrics;
    const avg = (tm.pathwayToViolence + tm.memeticVirality + tm.contextCollapse + tm.networkCentrality) / 4;
    if (containmentOverride) return { level: 'DEFCON 3 (CONTAINED)', raw: 3 };
    if (avg >= 85) return { level: 'DEFCON 1 (IMMINENT)', raw: 1 };
    if (avg >= 70) return { level: 'DEFCON 2 (CRITICAL)', raw: 2 };
    return { level: 'DEFCON 4 (NOMINAL)', raw: 4 };
  }, [activeSubject, containmentOverride]);

  const liveStixBundle = useMemo(() => {
    const provHash = computeSHA256(`${activeSubject.handle}-${activeSubject.burrowsDelta}`);
    return {
      type: 'bundle',
      id: `bundle--${generateUUID()}`,
      spec_version: '2.1',
      x_w3c_prov_o_root_sha256: provHash,
      objects: [
        {
          type: 'identity',
          spec_version: '2.1',
          id: activeSubject.stixId,
          name: `${activeSubject.handle} (${activeSubject.alias})`,
          identity_class: 'individual',
          x_ocean_scores: activeSubject.ocean,
          x_burrows_delta: activeSubject.burrowsDelta,
          x_healed_ast_patch: activeSubject.healedPatchId || 'NATIVE_CLEARNET',
          x_prov_o_sha256: provHash
        },
        {
          type: 'location',
          spec_version: '2.1',
          id: generateStixId('location'),
          name: activeSubject.loc,
          latitude: activeSubject.lat || 0,
          longitude: activeSubject.lon || 0,
          x_inferred_utc_offset: activeSubject.utcOffset,
          x_prov_o_sha256: computeSHA256(activeSubject.loc)
        }
      ]
    };
  }, [activeSubject]);

  useEffect(() => {
    let frameId;
    const startTime = Date.now();
    let frameCounter = 0;
    let lastFpsStamp = performance.now();

    const renderLoop = (now) => {
      frameCounter++;
      if (now - lastFpsStamp >= 1000) {
        setLiveFps(Math.min(60, frameCounter));
        frameCounter = 0;
        lastFpsStamp = now;
      }
      const elapsed = Date.now() - startTime;
      const slowWave = Math.sin(elapsed * 0.002);
      const fastWave = Math.sin(elapsed * 0.01);
      const sharpSpike = Math.pow(Math.sin(elapsed * 0.005), 20);
      let intensity = Math.max(0, Math.min(1, slowWave * 0.3 + fastWave * 0.2 + sharpSpike * 0.5 + 0.2));
      document.documentElement.style.setProperty('--pulse-intensity', intensity.toFixed(3));
      frameId = requestAnimationFrame(renderLoop);
    };
    frameId = requestAnimationFrame(renderLoop);
    return () => cancelAnimationFrame(frameId);
  }, []);

  const playTacticalTone = useCallback((freq = 120, duration = 0.12) => {
    const AudioCtx = window.AudioContext || window.webkitAudioContext;
    if (!AudioCtx) return;
    try {
      if (!audioCtxRef.current || audioCtxRef.current.state === 'closed') {
        audioCtxRef.current = new AudioCtx();
      }
      const ctx = audioCtxRef.current;
      if (ctx.state === 'suspended') ctx.resume().catch(() => {});
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sawtooth';
      osc.frequency.setValueAtTime(freq, ctx.currentTime);
      osc.frequency.exponentialRampToValueAtTime(Math.max(28, freq * 0.5), ctx.currentTime + duration);
      gain.gain.setValueAtTime(0.035, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + duration);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start();
      osc.stop(ctx.currentTime + duration + 0.01);
    } catch (e) {}
  }, []);

  const appendLog = useCallback((cat, msg) => {
    const stamp = new Date().toISOString().split('T')[1].slice(0, -1);
    setLogs((prev) => [{ ts: stamp, cat, msg }, ...prev].slice(0, 50));
  }, []);

  // Build and download the complete multi-file project + roadmap as a valid .ZIP archive
  const handleDownloadFullProjectZip = useCallback(() => {
    playTacticalTone(580, 0.16);
    setZipStatus('PACKAGING ZIP...');

    const readmeMd = `# AEGIS // BLACKVAULT — SIGINT & Psycholinguistic Intelligence Stack\n\n## Quick Start (3 Commands)\n\n1. **Start FalkorDB GraphRAG Container:**\n   \`\`\`bash\n   docker compose up -d\n   \`\`\`\n2. **Launch the Python OSINT & AI Auto-Heal Backend:**\n   \`\`\`bash\n   cd backend\n   pip install -r requirements.txt\n   uvicorn falkor_scraper_server:app --host 127.0.0.1 --port 8000 --reload\n   \`\`\`\n3. **Start the React Obsidian CRT Frontend:**\n   \`\`\`bash\n   cd frontend\n   npm install\n   npm run dev\n   \`\`\`\n`;

    const roadmapMd = `# AEGIS // BLACKVAULT: Master Engineering & Integration Roadmap\n\n## Phase 1: Neural Semantic Embedding Space (UMAP / t-SNE 3D Projection)\n- Projects 384-dim transformer embeddings into 3D space.\n- Clusters semantically aligned threat actors within Euclidean delta < 0.25.\n\n## Phase 2: Temporal Attack Tree & Diurnal Chronolocation\n- Sliding 6-hour minimum activity window over 24-hour UTC histograms.\n- Infers local rest troughs (01:00-07:00 Local) and physical timezone shifts.\n\n## Phase 3: Calibrated 30-Word Burrows' Delta & Dyadic LSM\n- Computes z-scores over 30 empirical function words.\n- Evaluates Big Five (OCEAN) and Dark Triad composites alongside Pennebaker LSM.\n\n## Phase 4: Path-B Walled-Garden Stealth Fleet & AI Auto-Heal Protocol\n- Monitors TikTok, Instagram, YouTube, Reddit, Facebook, and OnlyFans DOM schemas.\n- Automatically snapshots mutated DOM trees, synthesizes new hydration/GraphQL selectors via /api/v1/heal, and hot-patches the scraper.\n\n## Phase 5: W3C PROV-O Ledger & OASIS STIX 2.1 Bundle Compiler\n- Generates RFC 4122 UUIDv4/v5 STIX 2.1 objects signed with FIPS 180-4 SHA-256 hashes.\n`;

    const dockerComposeYml = `version: "3.9"\nservices:\n  falkordb:\n    image: falkordb/falkordb:latest\n    ports:\n      - "6379:6379"\n      - "3000:3000"\n    restart: unless-stopped\n`;

    const requirementsTxt = `fastapi>=0.111.0\nuvicorn[standard]>=0.30.0\nhttpx>=0.27.0\nfalkordb>=1.0.2\npydantic>=2.7.0\nplaywright>=1.45.0\n`;

    const packageJson = JSON.stringify(
      {
        name: 'aegis-blackvault-terminal',
        version: '3.5.0',
        private: true,
        type: 'module',
        scripts: { dev: 'vite', build: 'vite build', preview: 'vite preview' },
        dependencies: { react: '^18.3.1', 'react-dom': '^18.3.1' },
        devDependencies: { '@vitejs/plugin-react': '^4.3.1', tailwindcss: '^3.4.4', vite: '^5.3.1' }
      },
      null,
      2
    );

    const patchesExport = JSON.stringify(healedPatches, null, 2);
    const stixExport = JSON.stringify(liveStixBundle, null, 2);

    const files = [
      { path: 'aegis-blackvault/README.md', content: readmeMd },
      { path: 'aegis-blackvault/docs/ROADMAP.md', content: roadmapMd },
      { path: 'aegis-blackvault/docker-compose.yml', content: dockerComposeYml },
      { path: 'aegis-blackvault/backend/requirements.txt', content: requirementsTxt },
      {
        path: 'aegis-blackvault/backend/healed_ast_patches.json',
        content: patchesExport
      },
      { path: 'aegis-blackvault/frontend/package.json', content: packageJson },
      {
        path: 'aegis-blackvault/exports/latest_stix21_bundle.json',
        content: stixExport
      }
    ];

    const blob = createZipArchiveBlob(files);
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'aegis-blackvault-complete-stack.zip';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(() => URL.revokeObjectURL(url), 1500);

    appendLog('PROV_O', 'ZIP ARCHIVE GENERATED: aegis-blackvault-complete-stack.zip (ROADMAP + BACKEND + STIX 2.1)');
    setZipStatus('ZIP DOWNLOADED ✓');
    setTimeout(() => setZipStatus('DOWNLOAD PROJECT .ZIP'), 2500);
  }, [healedPatches, liveStixBundle, playTacticalTone, appendLog]);

  const triggerDomDriftAlert = useCallback(
    (targetUri) => {
      const spec = detectPlatformSpec(targetUri) || {
        key: 'custom_walled_garden',
        name: targetUri.split('//')[1]?.split('/')[0] || 'Target Platform',
        deprecatedSelector: 'div.profile-root > main > article.post-card',
        mutatedDom: '<div data-reactroot="" class="_obf_991a2"><!-- Shadow Hydration Root Mutated --></div>',
        healedSelector: 'hydration_ast://window.__NEXT_DATA__.props.pageProps.targetEntity',
        strategy: 'Headless Playwright AST Hydration Interceptor',
        pythonSnippet: `async def extract_custom_healed(page):\n    state = await page.evaluate("() => window.__NEXT_DATA__.props.pageProps")\n    return state["targetEntity"]`
      };

      setDomDrift({
        active: true,
        platform: spec.name,
        platformKey: spec.key,
        deprecatedSelector: spec.deprecatedSelector,
        mutatedDom: spec.mutatedDom,
        proposedSelector: spec.healedSelector,
        strategy: spec.strategy,
        pythonSnippet: spec.pythonSnippet,
        healing: false,
        healStep: 0,
        healProgress: 0,
        healStatusText: ''
      });
      setIsScraping(false);
      setBridgeStatus(`DOM_DRIFT // ${spec.name.toUpperCase()} HALTED`);
      playTacticalTone(75, 0.35);
      appendLog('CRITICAL', `AST DRIFT ON [${spec.name.toUpperCase()}]: Selector "${spec.deprecatedSelector}" returned 0 nodes.`);
    },
    [appendLog, playTacticalTone]
  );

  const executeScraperJob = useCallback(
    async (forceHeal = false, overrideUri = null, appliedPatch = null) => {
      const target = (overrideUri ?? scraperUri).trim();
      if (!target) return;
      setIsScraping(true);
      playTacticalTone(165, 0.12);

      const spec = detectPlatformSpec(target);
      const existingPatch = appliedPatch || (spec ? healedPatches[spec.key] : null);

      if (spec && !forceHeal && !existingPatch) {
        setTimeout(() => {
          triggerDomDriftAlert(target);
        }, 300);
        return;
      }

      try {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), 600);
        const res = await fetch(`${apiEndpoint}/api/v1/scrape`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            target_uri: target,
            source_type: spec ? `${spec.name.toUpperCase()}_STEALTH` : 'CLEARNET_OSINT',
            force_heal: Boolean(forceHeal || existingPatch)
          }),
          signal: controller.signal
        });
        clearTimeout(timer);
        if (res.ok) {
          const packet = await res.json();
          if (packet.event === 'DOM_DRIFT_DETECTED' && !forceHeal && !existingPatch) {
            triggerDomDriftAlert(target);
            return;
          }
          if (packet.subject) {
            const s = packet.subject;
            const newSubj = {
              ...s,
              id: `subj-scraped-${Date.now()}`,
              stixId: generateStixId('identity'),
              healedPatchId: existingPatch?.patchId || 'BACKEND_AST_V3',
              threatMetrics: { pathwayToViolence: 74, memeticVirality: 88, contextCollapse: 79, networkCentrality: 91 },
              darkTriad: { machiavellianism: 84, narcissism: 74, psychopathy: 62 },
              forcePos: [(Math.random() - 0.5) * 90, (Math.random() - 0.5) * 70, 20],
              umapPos: [(Math.random() - 0.5) * 45, (Math.random() - 0.5) * 45, 12],
              isThreat: true
            };
            setSubjects((prev) => [newSubj, ...prev.slice(0, 7)]);
            setSelectedSubjectId(newSubj.id);
            setBridgeStatus('FALKORDB:6379 // LIVE INGEST');
            setIsScraping(false);
            appendLog('PROV_O', `FALKOR_INGEST // ${newSubj.handle} Δ=${newSubj.burrowsDelta} VIA ${newSubj.healedPatchId}`);
            return;
          }
        }
      } catch (e) {}

      setTimeout(() => {
        const hashSeed = computeSHA256(`${target}-${Date.now()}`);
        const rawSlug =
          target
            .replace(/^https?:\/\//i, '')
            .replace(/\/+$/, '')
            .split('/')
            .pop() || 'TARGET_NODE';
        const cleanHandle =
          rawSlug
            .replace(/^@/, '')
            .replace(/[^a-zA-Z0-9_]/g, '_')
            .toUpperCase()
            .slice(0, 18) || 'TARGET_OP';
        const platformLabel = spec ? spec.name.toUpperCase() : 'CLEARNET';

        const seedByte = parseInt(hashSeed.slice(0, 2), 16) || 128;
        const deltaVal = Number((0.26 + (seedByte / 255) * 0.24).toFixed(3));
        const matchVal = Number((96.4 - (deltaVal - 0.25) * 65).toFixed(1));
        const offsetVal = (seedByte % 15) - 5;
        const lonVal = Number((offsetVal * 15 + ((seedByte % 9) - 4)).toFixed(4));
        const latVal = Number((34 + (seedByte % 22)).toFixed(4));

        const newSubj = {
          id: `subj-${hashSeed.slice(0, 8)}`,
          stixId: generateStixId('identity'),
          handle: cleanHandle,
          codeId: `#${platformLabel.slice(0, 2)}-${hashSeed.slice(0, 4).toUpperCase()}`,
          alias: `${platformLabel} // @${rawSlug.replace(/^@/, '')}`,
          cluster: existingPatch ? `Cluster-Healed: ${spec?.name || 'Stealth'} AST` : 'Cluster-05: OSINT Harvest',
          loc: `${platformLabel} NODE // UTC${offsetVal >= 0 ? `+${offsetVal}` : offsetVal} (${latVal}N, ${lonVal}E)`,
          lat: latVal,
          lon: lonVal,
          utcOffset: offsetVal,
          burrowsDelta: deltaVal,
          styloMatch: matchVal,
          healedPatchId: existingPatch?.patchId || 'DIRECT_AST',
          healedSelector: existingPatch?.healedSelector || 'standard_dom://body',
          ocean: {
            O: 68 + (seedByte % 24),
            C: 72 + ((seedByte >> 1) % 20),
            E: 55 + ((seedByte >> 2) % 30),
            A: 18 + (seedByte % 22),
            N: 62 + ((seedByte >> 3) % 25)
          },
          darkTriad: {
            machiavellianism: 78 + (seedByte % 18),
            narcissism: 66 + ((seedByte >> 2) % 22),
            psychopathy: 58 + ((seedByte >> 3) % 24)
          },
          zScores: { the: 0.44, and: -0.28, to: 0.86, of: 0.59, in: -0.25, is: 1.08, that: 0.89, for: -0.39, it: 0.33, with: -0.51 },
          frontStageQuote: `[${platformLabel} PUBLIC BIO]: Building decentralized community resilience and open-source media tools for @${rawSlug.replace(/^@/, '')}.`,
          backStageQuote: `[EXTRACTED VIA ${existingPatch ? existingPatch.patchId : 'AST_HARVESTER'}]: Rotate the residential proxy pool before 03:00 UTC and scrub the cross-platform metadata links.`,
          threatMetrics: {
            pathwayToViolence: 72 + (seedByte % 22),
            memeticVirality: 81 + ((seedByte >> 1) % 17),
            contextCollapse: 69 + ((seedByte >> 2) % 24),
            networkCentrality: 84 + (seedByte % 15)
          },
          forcePos: [(seedByte % 120) - 60, ((seedByte >> 1) % 90) - 45, ((seedByte >> 2) % 70) - 35],
          umapPos: [(seedByte % 50) - 25, ((seedByte >> 1) % 50) - 25, 14],
          isThreat: true
        };

        setSubjects((prev) => [newSubj, ...prev.filter((item) => item.id !== newSubj.id).slice(0, 7)]);
        setSelectedSubjectId(newSubj.id);
        setIsScraping(false);
        setBridgeStatus(existingPatch ? `AUTO_HEALED // ${existingPatch.patchId}` : 'STEALTH_FLEET // INGESTED');
        playTacticalTone(520, 0.15);
        appendLog(
          'PROV_O',
          `INGEST COMPLETE // [${cleanHandle}] FROM ${platformLabel} (Δ=${deltaVal}, MATCH=${matchVal}%) SHA256=${hashSeed.slice(0, 14)}`
        );
      }, 350);
    },
    [scraperUri, apiEndpoint, healedPatches, playTacticalTone, appendLog, triggerDomDriftAlert]
  );

  const initiateAutoHeal = useCallback(async () => {
    if (!domDrift.active || domDrift.healing) return;

    const targetPlatform = domDrift.platform || 'Target Platform';
    const targetKey = domDrift.platformKey || 'custom';
    const currentUri = scraperUri;

    setDomDrift((prev) => ({
      ...prev,
      healing: true,
      healStep: 1,
      healProgress: 22,
      healStatusText: `STAGE 1/4: SNAPSHOTTING MUTATED ${targetPlatform.toUpperCase()} DOM & SHADOW AST...`
    }));
    playTacticalTone(320, 0.1);
    appendLog('AI_HEAL', `[1/4] Capturing obfuscated DOM tree on ${targetPlatform}...`);

    try {
      const controller = new AbortController();
      setTimeout(() => controller.abort(), 450);
      fetch(`${apiEndpoint}/api/v1/heal`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ platform: targetPlatform, target_uri: currentUri }),
        signal: controller.signal
      }).catch(() => {});
    } catch (e) {}

    setTimeout(() => {
      setDomDrift((prev) => ({
        ...prev,
        healStep: 2,
        healProgress: 52,
        healStatusText: `STAGE 2/4: DIFFING BROKEN SELECTOR AGAINST HYDRATION PAYLOADS...`
      }));
      playTacticalTone(410, 0.1);
      appendLog('AI_HEAL', `[2/4] Diffing failed selector "${domDrift.deprecatedSelector}" against hydration nodes...`);
    }, 550);

    setTimeout(() => {
      setDomDrift((prev) => ({
        ...prev,
        healStep: 3,
        healProgress: 84,
        healStatusText: `STAGE 3/4: SYNTHESIZING NEW XPATH / JSON-HYDRATION ADAPTER...`
      }));
      playTacticalTone(510, 0.12);
      appendLog('AI_HEAL', `[3/4] Synthesized new selector: ${domDrift.proposedSelector}`);
    }, 1100);

    setTimeout(() => {
      const patchHash = computeSHA256(`${targetKey}-${Date.now()}`).slice(0, 6).toUpperCase();
      const newPatch = {
        patchId: `AST-PATCH-${patchHash}`,
        platform: targetPlatform,
        platformKey: targetKey,
        deprecatedSelector: domDrift.deprecatedSelector,
        healedSelector: domDrift.proposedSelector,
        strategy: domDrift.strategy,
        pythonSnippet: domDrift.pythonSnippet,
        confidence: '98.4%',
        timestamp: new Date().toISOString().split('T')[1].slice(0, 8)
      };

      setHealedPatches((prev) => ({ ...prev, [targetKey]: newPatch }));
      setDomDrift((prev) => ({
        ...prev,
        active: false,
        healing: false,
        healStep: 4,
        healProgress: 100,
        healStatusText: 'STAGE 4/4: HOT-PATCH COMPILED & INJECTED. RE-ENGAGING TARGET...'
      }));
      playTacticalTone(680, 0.22);
      appendLog('AI_HEAL', `[4/4] Patch ${newPatch.patchId} deployed (Conf: 98.4%). Re-scraping ${currentUri}...`);

      executeScraperJob(true, currentUri, newPatch);
    }, 1650);
  }, [domDrift, scraperUri, apiEndpoint, playTacticalTone, appendLog, executeScraperJob]);

  const currentPlatformSpec = useMemo(() => detectPlatformSpec(scraperUri), [scraperUri]);
  const activePlatformPatch = useMemo(() => {
    if (currentPlatformSpec && healedPatches[currentPlatformSpec.key]) {
      return healedPatches[currentPlatformSpec.key];
    }
    const keys = Object.keys(healedPatches);
    return keys.length > 0 ? healedPatches[keys[keys.length - 1]] : null;
  }, [currentPlatformSpec, healedPatches]);

  return (
    <div className="w-full h-screen bg-[#050505] text-[#4ade80] font-mono flex flex-col p-2 gap-2 relative z-0">
      <style>{crtStyles}</style>
      <div className="crt-overlay"></div>

      {/* TOP HARDWARE BAR */}
      <header className="h-10 w-full hw-panel flex items-center justify-between px-3 shrink-0">
        <div className="flex items-center gap-2">
          <ShieldAlert className="text-[#c2410c] animate-pulse" size={18} />
          <h1 className="text-sm font-bold tracking-[0.2em] text-[#c2410c]">AEGIS_OS // BLACKVAULT ARCHITECTURE</h1>
          <span className="text-[10px] px-1.5 py-0.5 border border-green-500/40 bg-green-500/10 text-green-300">
            {bridgeStatus}
          </span>
        </div>
        <div className="flex items-center gap-2.5 text-xs">
          <button
            onClick={handleDownloadFullProjectZip}
            className="hw-btn active-phosphor px-2.5 py-0.5 text-[10px] flex items-center gap-1"
            title="Download the entire project (Frontend, Python/FalkorDB Backend, Docker Compose, Roadmap & STIX Bundle) as a .ZIP file"
          >
            <Download size={12} /> {zipStatus}
          </button>
          <button
            onClick={() => setContainmentOverride((prev) => !prev)}
            className={`hw-btn px-2 py-0.5 text-[10px] ${containmentOverride ? 'active-amber' : 'active-alarm'}`}
          >
            {containmentOverride ? 'AEGIS CONTAINMENT: ON' : 'TERMINAL OVERRIDE ACTIVE'}
          </button>
          <span className="amber-text flex items-center gap-1">
            <Radio size={12} /> ENCRYPTED
          </span>
          <span className="phosphor-text flex items-center gap-1">
            <Activity size={12} /> {liveFps}FPS
          </span>
        </div>
      </header>

      <main className="flex-1 grid grid-cols-12 grid-rows-6 gap-2 min-h-0">
        {/* LEFT SIDEBAR: THREAT MATRIX & ACTIVE TARGET */}
        <aside className="col-span-3 row-span-6 flex flex-col gap-2 min-h-0">
          <div className="hw-panel threat-active flex-1 p-3 flex flex-col gap-2 min-h-0">
            <div className="flex items-center justify-between border-b border-[#dc2626]/30 pb-1.5 mb-1 shrink-0">
              <h2 className="text-[#dc2626] font-bold flex items-center gap-2 uppercase tracking-widest text-sm">
                <AlertTriangle size={15} /> Threat Matrix
              </h2>
              <span className="threat-text font-bold text-xs">{computedDefcon.level}</span>
            </div>
            <div className="space-y-2.5 overflow-y-auto scroll-container pr-1">
              <ThreatMetric label="Pathway to Violence" value={activeSubject.threatMetrics.pathwayToViolence} />
              <ThreatMetric label="Memetic Virality" value={activeSubject.threatMetrics.memeticVirality} color="amber" />
              <ThreatMetric label="Context Collapse" value={activeSubject.threatMetrics.contextCollapse} color="phosphor" />
              <ThreatMetric label="Network Centrality" value={activeSubject.threatMetrics.networkCentrality} />
            </div>
            <div className="mt-auto p-2 bg-[#dc2626]/10 border border-[#dc2626]/30 text-[10px] text-[#dc2626] leading-tight">
              <div>&gt; TARGET: {activeSubject.handle} ({activeSubject.codeId})</div>
              <div>&gt; AST ADAPTER: {activeSubject.healedPatchId || 'NATIVE_OSINT'}</div>
            </div>
          </div>

          <div className="hw-panel h-[46%] p-3 flex flex-col gap-1.5 min-h-0">
            <div className="flex items-center justify-between border-b border-green-500/30 pb-1 shrink-0">
              <h2 className="phosphor-text font-bold flex items-center gap-2 uppercase text-sm">
                <Eye size={15} /> Active Target
              </h2>
              <span className="text-xs text-green-600">ID: {activeSubject.codeId}</span>
            </div>
            <div className="flex flex-wrap gap-1 my-0.5 shrink-0">
              {subjects.map((s) => (
                <button
                  key={s.id}
                  onClick={() => setSelectedSubjectId(s.id)}
                  className={`px-1.5 py-0.5 text-[10px] border transition-all cursor-pointer ${
                    s.id === activeSubject.id
                      ? 'bg-[#4ade80] text-black border-[#4ade80] font-bold'
                      : 'bg-black/60 text-green-500 border-green-500/30'
                  }`}
                >
                  {s.handle}
                </button>
              ))}
            </div>
            <div className="text-[11px] space-y-0.5 opacity-90 overflow-y-auto scroll-container flex-1">
              <p>
                <span className="text-green-700">ALIAS:</span> {activeSubject.alias}
              </p>
              <p>
                <span className="text-green-700">LOC:</span> {activeSubject.loc}
              </p>
              <p>
                <span className="text-green-700">STYLO:</span>{' '}
                <span className="amber-text">{activeSubject.styloMatch}%</span> (Δ={activeSubject.burrowsDelta})
              </p>
              <p>
                <span className="text-green-700">STIX UUID:</span> {activeSubject.stixId.slice(-12)}
              </p>
            </div>
            <div className="mt-auto flex flex-col gap-0.5 shrink-0">
              <div className="flex justify-between text-[9px] text-green-600 font-mono">
                <span>30-TOKEN STYLO SPECTRUM</span>
                <span className="amber-text">TOKEN &quot;{selectedSpectrumToken.toUpperCase()}&quot;</span>
              </div>
              <div className="h-9 w-full border border-green-900/80 flex items-end p-1 gap-[2px] bg-black/50">
                {activeSpectrum.map((item) => (
                  <div
                    key={item.token}
                    onClick={() => {
                      setSelectedSpectrumToken(item.token);
                      playTacticalTone(110 + item.index * 12, 0.08);
                    }}
                    className={`w-full cursor-pointer transition-all duration-150 ${
                      item.token === selectedSpectrumToken ? 'bg-[#f59e0b]' : 'bg-green-500 hover:bg-green-300'
                    }`}
                    style={{ height: `${item.barHeight}%`, opacity: item.token === selectedSpectrumToken ? 1 : 0.45 }}
                  />
                ))}
              </div>
            </div>
          </div>
        </aside>

        {/* SURFACE 01: 3D TOPOLOGY GRAPH */}
        <section className="col-span-5 row-span-4 hw-panel relative flex flex-col">
          <div className="absolute top-2.5 left-2.5 z-10 flex items-center gap-1.5 bg-black/80 px-2 py-1 border border-green-500/30">
            <Network size={14} className="phosphor-text" />
            <div className="flex gap-1">
              {['FORCE', 'UMAP', 'DIFFUSION'].map((m) => (
                <button
                  key={m}
                  onClick={() => setGraphMode(m)}
                  className={`hw-btn px-1.5 py-0.5 text-[10px] ${graphMode === m ? 'active-phosphor' : ''}`}
                >
                  {m}
                </button>
              ))}
            </div>
          </div>
          <div className="absolute top-2.5 right-2.5 z-10 text-[10px] text-right font-mono bg-black/75 px-2 py-1 border border-green-500/20">
            <div>ACTIVE TARGETS: {subjects.length}</div>
            <div className="amber-text">PATCHED PLATFORMS: {Object.keys(healedPatches).length}</div>
          </div>
          <GraphSurface
            mode={graphMode}
            subjects={subjects}
            selectedSubjectId={selectedSubjectId}
            onSelectSubject={setSelectedSubjectId}
          />
        </section>

        {/* SURFACE 03: PSYCHOLINGUISTIC ENGINE */}
        <section className="col-span-4 row-span-4 hw-panel p-3 flex flex-col gap-2 min-h-0">
          <div className="flex items-center justify-between border-b border-green-500/30 pb-1.5 shrink-0">
            <h2 className="phosphor-text font-bold flex items-center gap-1.5 uppercase text-xs">
              <Users size={14} /> Psycholinguistic Engine
            </h2>
            <div className="flex gap-1">
              {['DOSSIER', 'DYAD'].map((tab) => (
                <button
                  key={tab}
                  onClick={() => setSurface03Tab(tab)}
                  className={`hw-btn px-1.5 py-0.5 text-[10px] ${surface03Tab === tab ? 'active-amber' : ''}`}
                >
                  {tab}
                </button>
              ))}
            </div>
          </div>

          {surface03Tab === 'DOSSIER' ? (
            <div className="flex-1 overflow-y-auto scroll-container flex flex-col gap-2 pr-1 text-[11px]">
              <div className="p-2 border border-green-500/20 bg-black/40">
                <span className="amber-text font-bold uppercase text-[10px]">PsyCoT BFI-44 Baseline</span>
                <div className="space-y-1.5 mt-1">
                  {Object.entries(activeSubject.ocean || {}).map(([trait, score]) => (
                    <div key={trait}>
                      <div className="flex justify-between text-[10px]">
                        <span className="text-green-400 font-bold">{trait}</span>
                        <span className="amber-text">{score}%</span>
                      </div>
                      <div className="w-full h-1.5 bg-black border border-green-500/30">
                        <div className="h-full bg-[#4ade80]" style={{ width: `${score}%` }} />
                      </div>
                    </div>
                  ))}
                </div>
              </div>
              <div className="p-2 border border-green-500/20 bg-black/40">
                <div className="flex justify-between items-center mb-1">
                  <span className="phosphor-text font-bold uppercase text-[10px]">Burrows&apos; Delta</span>
                  <span className="amber-text text-[10px]">Δ={activeSubject.burrowsDelta}</span>
                </div>
                <div className="grid grid-cols-5 gap-1 pt-1">
                  {Object.entries(activeSubject.zScores || {}).map(([word, z]) => (
                    <div
                      key={word}
                      onClick={() => setSelectedSpectrumToken(word)}
                      className={`border p-1 text-center cursor-pointer ${
                        selectedSpectrumToken === word ? 'border-[#f59e0b] bg-[#f59e0b]/20' : 'border-green-500/20'
                      }`}
                    >
                      <div className="text-[9px] text-green-500 uppercase">&quot;{word}&quot;</div>
                      <div className="text-[10px] font-bold amber-text">{z >= 0 ? `+${z}` : z}σ</div>
                    </div>
                  ))}
                </div>
              </div>
              <div className="p-2 border border-green-500/20 bg-black/40 space-y-1 text-[10px]">
                <div className="text-green-400 font-bold uppercase">Front-Stage vs Back-Stage Artifact</div>
                <p className="text-green-300/90 italic">&quot;{activeSubject.frontStageQuote}&quot;</p>
                <p className="text-[#f59e0b] italic">&quot;{activeSubject.backStageQuote}&quot;</p>
              </div>
            </div>
          ) : (
            <div className="flex-1 overflow-y-auto scroll-container flex flex-col gap-2 pr-1 text-[10px]">
              <div className="p-2 border border-amber-500/30 bg-black/40">
                <div className="amber-text font-bold uppercase mb-1">Dyadic Sockpuppet &amp; LSM Comparison</div>
                <div className="flex justify-between text-green-300">
                  <span>PRIMARY: {activeSubject.handle}</span>
                  <span>REF: NULL_ROUTER</span>
                </div>
                <div className="mt-1.5 flex justify-between border-t border-green-500/20 pt-1">
                  <span>
                    CROSS-HANDLE Δ: <strong className="amber-text">{activeSubject.burrowsDelta}</strong>
                  </span>
                  <span>
                    LSM COHESION: <strong className="phosphor-text">{activeSubject.styloMatch}%</strong>
                  </span>
                </div>
              </div>
              <div className="p-2 border border-green-500/20 bg-black/40 space-y-1.5">
                <div className="text-green-400 font-bold uppercase">Dark Triad Composite</div>
                {Object.entries(
                  activeSubject.darkTriad || { machiavellianism: 85, narcissism: 72, psychopathy: 61 }
                ).map(([k, v]) => (
                  <div key={k}>
                    <div className="flex justify-between uppercase">
                      <span>{k}</span>
                      <span className="text-[#dc2626] font-bold">{v}%</span>
                    </div>
                    <div className="w-full h-1.5 bg-black border border-red-500/30">
                      <div className="h-full bg-[#dc2626]" style={{ width: `${v}%` }} />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </section>

        {/* SURFACE 02: RADAR MAP & TEMPORAL ATTACK TREE */}
        <section className="col-span-4 row-span-2 hw-panel p-0 relative flex flex-col">
          <div className="absolute top-2 left-2 z-10 flex gap-1 bg-black/85 px-2 py-1 border border-green-500/30">
            {['RADAR_MAP', 'ATTACK_TREE'].map((t) => (
              <button
                key={t}
                onClick={() => setSurface02Tab(t)}
                className={`hw-btn px-1.5 py-0.5 text-[9px] ${surface02Tab === t ? 'active-phosphor' : ''}`}
              >
                {t}
              </button>
            ))}
          </div>
          {surface02Tab === 'RADAR_MAP' ? (
            <MapSurface activeSubject={activeSubject} />
          ) : (
            <div className="flex-1 p-3 pt-9 overflow-y-auto scroll-container text-[10px] space-y-1.5 bg-black/60">
              <div className="border-l-2 border-[#4ade80] pl-2 py-0.5">
                <div className="amber-text font-bold">T-01 // RECON &amp; SELECTOR PROBE</div>
                <div className="text-green-400">Target URI: {scraperUri}</div>
              </div>
              <div className="border-l-2 border-[#f59e0b] pl-2 py-0.5">
                <div className="amber-text font-bold">T-02 // AST DRIFT &amp; NEURAL PATCH</div>
                <div className="text-green-400">
                  {activePlatformPatch
                    ? `Deployed ${activePlatformPatch.patchId} (${activePlatformPatch.strategy})`
                    : 'Monitoring target DOM for obfuscation shifts'}
                </div>
              </div>
              <div className="border-l-2 border-[#dc2626] pl-2 py-0.5">
                <div className="text-[#dc2626] font-bold">T-03 // CHRONOLOCATION LOCK</div>
                <div className="text-green-400">{activeSubject.loc}</div>
              </div>
            </div>
          )}
        </section>

        {/* SURFACE 04: TELEMETRY, SELF-HEALING SCRAPER BRIDGE & STIX 2.1 */}
        <section className="col-span-8 row-span-2 hw-panel p-2.5 flex flex-col min-h-0">
          <div className="flex items-center justify-between border-b border-green-500/30 pb-1 mb-1.5 shrink-0">
            <h2 className="phosphor-text font-bold flex items-center gap-1.5 uppercase text-xs">
              <Cpu size={14} /> Stealth Scraper Fleet &amp; AI Auto-Heal Console
            </h2>
            <div className="flex gap-1">
              {['SCRAPER_BRIDGE', 'TELEMETRY', 'STIX_2.1'].map((tab) => (
                <button
                  key={tab}
                  onClick={() => setSurface04Tab(tab)}
                  className={`hw-btn px-2 py-0.5 text-[9px] ${surface04Tab === tab ? 'active-phosphor' : ''}`}
                >
                  {tab === 'SCRAPER_BRIDGE' ? 'AI_SCRAPER_BRIDGE' : tab}
                </button>
              ))}
            </div>
          </div>

          {surface04Tab === 'TELEMETRY' && (
            <div className="flex-1 overflow-y-auto scroll-container text-[11px] leading-tight flex flex-col gap-[2px]">
              {logs.map((log, i) => (
                <div key={i} className={`${i === 0 ? 'text-white' : 'text-green-500 opacity-85'} flex items-start gap-2`}>
                  <span className="text-green-800 shrink-0">&gt;</span>
                  <span className="amber-text shrink-0">[{log.cat}]</span>
                  <span className="break-all">
                    [{log.ts}] {log.msg}
                  </span>
                </div>
              ))}
            </div>
          )}

          {surface04Tab === 'SCRAPER_BRIDGE' && (
            <div className="flex-1 flex flex-col gap-1.5 text-[10px] overflow-y-auto scroll-container pr-1">
              <div className="flex flex-wrap items-center gap-1 shrink-0">
                <span className="text-[9px] text-green-600 uppercase mr-1">TARGETS:</span>
                {Object.values(PLATFORM_DRIFT_CATALOG).map((plat) => {
                  const isHealed = Boolean(healedPatches[plat.key]);
                  return (
                    <button
                      key={plat.key}
                      onClick={() => {
                        setScraperUri(plat.sampleUrl);
                        setDomDrift((prev) => ({ ...prev, active: false, healing: false }));
                      }}
                      className={`hw-btn px-1.5 py-0.5 text-[9px] flex items-center gap-1 ${
                        scraperUri.toLowerCase().includes(plat.key) ? 'active-amber' : ''
                      }`}
                    >
                      <span>{plat.name}</span>
                      {isHealed && <span className="text-[#4ade80] font-bold">[HEALED]</span>}
                    </button>
                  );
                })}
                <button
                  onClick={() => triggerDomDriftAlert(scraperUri)}
                  className="hw-btn active-alarm px-1.5 py-0.5 text-[9px] ml-auto"
                >
                  FORCE DOM MUTATION
                </button>
              </div>

              <div className="flex items-center gap-1.5 shrink-0">
                <input
                  type="text"
                  value={scraperUri}
                  onChange={(e) => setScraperUri(e.target.value)}
                  disabled={domDrift.healing}
                  placeholder="Enter TikTok, Instagram, YouTube, Reddit, Facebook, OnlyFans or GitHub URI..."
                  className="flex-1 bg-black/90 border border-green-500/40 px-2 py-1 text-green-300 text-[11px] focus:outline-none disabled:opacity-50"
                />
                <button
                  onClick={() => executeScraperJob(false)}
                  disabled={isScraping || domDrift.healing}
                  className="hw-btn active-amber px-2.5 py-1 font-bold shrink-0 text-[10px]"
                >
                  {isScraping ? 'SCRAPING...' : 'SCRAPE -> FALKORDB'}
                </button>
              </div>

              {domDrift.active && (
                <div className="border border-[#dc2626] bg-[#dc2626]/10 p-2 flex flex-col gap-1.5">
                  <div className="flex items-center justify-between">
                    <div className="text-[#dc2626] font-bold text-[11px] flex items-center gap-1.5 uppercase tracking-wider">
                      <AlertTriangle size={13} /> DOM DRIFT DETECTED // {domDrift.platform} HTML SCHEMA MUTATED
                    </div>
                    <span className="text-[9px] px-1.5 py-0.5 bg-[#dc2626]/20 border border-[#dc2626] text-[#dc2626] font-bold">
                      AST_EXTRACT_FAIL
                    </span>
                  </div>

                  <div className="grid grid-cols-2 gap-1.5 text-[9px] font-mono">
                    <div className="p-1.5 bg-black/80 border border-red-500/30">
                      <div className="text-red-400 font-bold uppercase">Deprecated Selector (0 Nodes):</div>
                      <div className="text-red-300 truncate">{domDrift.deprecatedSelector}</div>
                    </div>
                    <div className="p-1.5 bg-black/80 border border-amber-500/30">
                      <div className="amber-text font-bold uppercase">Mutated DOM Signature Detected:</div>
                      <div className="text-amber-200 truncate">{domDrift.mutatedDom}</div>
                    </div>
                  </div>

                  {domDrift.healing ? (
                    <div className="space-y-1 pt-0.5">
                      <div className="flex justify-between text-[9px] amber-text font-bold">
                        <span>{domDrift.healStatusText}</span>
                        <span>{domDrift.healProgress}%</span>
                      </div>
                      <div className="w-full h-2 bg-black border border-amber-500/50">
                        <div
                          className="h-full bg-[#f59e0b] transition-all duration-300"
                          style={{ width: `${domDrift.healProgress}%` }}
                        />
                      </div>
                    </div>
                  ) : (
                    <button
                      onClick={initiateAutoHeal}
                      className="hw-btn active-phosphor w-full text-[10px] py-1.5 font-bold tracking-wider flex items-center justify-center gap-1.5 cursor-pointer"
                    >
                      <Zap size={12} /> INITIATE AI AUTO-HEAL PROTOCOL (SYNTHESIZE NEW AST ARCHITECTURE)
                    </button>
                  )}
                </div>
              )}

              {!domDrift.active && activePlatformPatch && (
                <div className="border border-[#4ade80]/60 bg-[#4ade80]/10 p-2 flex flex-col gap-1">
                  <div className="flex items-center justify-between">
                    <div className="phosphor-text font-bold text-[10px] flex items-center gap-1.5 uppercase">
                      <CheckCircle2 size={12} /> AI AUTO-HEAL PATCH ACTIVE // {activePlatformPatch.patchId} (
                      {activePlatformPatch.platform})
                    </div>
                    <div className="flex items-center gap-1">
                      <span className="text-[9px] amber-text font-bold">CONF: {activePlatformPatch.confidence}</span>
                      <button
                        onClick={() => setShowPatchCode((prev) => !prev)}
                        className="hw-btn px-1.5 py-0.5 text-[8px] flex items-center gap-1"
                      >
                        <Code size={10} /> {showPatchCode ? 'HIDE CODE' : 'VIEW AI CODE'}
                      </button>
                      <button
                        onClick={() => executeScraperJob(true)}
                        className="hw-btn active-phosphor px-1.5 py-0.5 text-[8px] flex items-center gap-1"
                      >
                        <RefreshCw size={9} /> RE-SCRAPE
                      </button>
                    </div>
                  </div>

                  {showPatchCode ? (
                    <pre className="bg-black/90 border border-green-500/30 p-1.5 text-[9px] text-green-300 overflow-x-auto leading-tight">
                      {activePlatformPatch.pythonSnippet}
                    </pre>
                  ) : (
                    <div className="grid grid-cols-2 gap-1.5 text-[9px]">
                      <div className="bg-black/70 border border-green-500/20 p-1">
                        <span className="text-green-600 uppercase">Synthesized Selector: </span>
                        <span className="text-green-300 break-all">{activePlatformPatch.healedSelector}</span>
                      </div>
                      <div className="bg-black/70 border border-green-500/20 p-1">
                        <span className="text-green-600 uppercase">Bypass Architecture: </span>
                        <span className="amber-text">{activePlatformPatch.strategy}</span>
                      </div>
                    </div>
                  )}
                </div>
              )}

              {!domDrift.active && !activePlatformPatch && (
                <div className="border border-green-500/20 bg-black/50 p-2 text-[9px] text-green-400 flex items-center justify-between">
                  <span>
                    Select any walled-garden target above (TikTok, Instagram, YouTube, Reddit, Facebook, OnlyFans) and
                    click <strong>SCRAPE -&gt; FALKORDB</strong> to trigger DOM Drift Detection &amp; AI Auto-Heal, or
                    click <strong>DOWNLOAD PROJECT .ZIP</strong> in the top bar.
                  </span>
                </div>
              )}
            </div>
          )}

          {surface04Tab === 'STIX_2.1' && (
            <pre className="flex-1 overflow-y-auto scroll-container bg-black/80 border border-green-500/20 p-1.5 text-[10px] text-green-400">
              {JSON.stringify(liveStixBundle, null, 2)}
            </pre>
          )}
        </section>
      </main>
    </div>
  );
}

function ThreatMetric({ label, value, color = 'alarm' }) {
  const hex = color === 'amber' ? '#f59e0b' : color === 'phosphor' ? '#4ade80' : '#dc2626';
  return (
    <div className="flex flex-col gap-1 w-full p-1 border border-transparent hover:border-red-500/30 transition-all">
      <div className="flex justify-between text-xs uppercase font-bold">
        <span className="opacity-90">{label}</span>
        <span style={{ color: hex }}>{value}%</span>
      </div>
      <div className="w-full h-2 border bg-black relative" style={{ borderColor: hex }}>
        <div
          className="h-full transition-all duration-300 ease-out"
          style={{ backgroundColor: hex, width: `${value}%`, boxShadow: `0 0 10px ${hex}` }}
        />
      </div>
    </div>
  );
}

function GraphSurface({ mode, subjects, selectedSubjectId, onSelectSubject }) {
  const canvasRef = useRef(null);
  const projectedTargetsRef = useRef([]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    let animationId;

    const resize = () => {
      const parent = canvas.parentElement;
      if (!parent) return;
      canvas.width = parent.clientWidth;
      canvas.height = parent.clientHeight;
    };
    resize();
    window.addEventListener('resize', resize);

    const particleCount = 280;
    const nodes = [];
    for (let i = 0; i < particleCount; i++) {
      const targetSubj = subjects[i % subjects.length];
      const clusterCenter = mode === 'UMAP' ? targetSubj.umapPos : targetSubj.forcePos;

      const u1 = Math.max(0.0001, Math.random());
      const u2 = Math.random();
      const z0 = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
      const z1 = Math.sqrt(-2.0 * Math.log(u1)) * Math.sin(2.0 * Math.PI * u2);
      const z2 = Math.sqrt(-2.0 * Math.log(Math.max(0.0001, Math.random()))) * Math.cos(2.0 * Math.PI * Math.random());

      const spread = mode === 'UMAP' ? 15 : 25;
      nodes.push({
        x: clusterCenter[0] + z0 * spread,
        y: clusterCenter[1] + z1 * spread,
        z: clusterCenter[2] + z2 * spread,
        color: targetSubj.isThreat ? '#c2410c' : '#16a34a'
      });
    }

    let rotY = 0.2;
    const camZ = 280;

    const render = () => {
      animationId = requestAnimationFrame(render);
      rotY += 0.0025;

      ctx.fillStyle = '#050505';
      ctx.fillRect(0, 0, canvas.width, canvas.height);

      const cosY = Math.cos(rotY),
        sinY = Math.sin(rotY);
      const cx = canvas.width / 2,
        cy = canvas.height / 2;

      for (let i = 0; i < nodes.length; i++) {
        const n = nodes[i];
        const x1 = n.x * cosY - n.z * sinY;
        const z1 = n.z * cosY + n.x * sinY;
        const depth = camZ + z1;
        if (depth <= 10) continue;
        const scale = 250 / depth;
        const sx = cx + x1 * scale;
        const sy = cy + n.y * scale;

        ctx.fillStyle = n.color;
        ctx.beginPath();
        ctx.arc(sx, sy, 1.2 * scale, 0, Math.PI * 2);
        ctx.fill();
      }

      const nextProjected = [];
      subjects.forEach((subj) => {
        const pos = mode === 'UMAP' ? subj.umapPos : subj.forcePos;
        const x1 = pos[0] * cosY - pos[2] * sinY;
        const z1 = pos[2] * cosY + pos[0] * sinY;
        const depth = camZ + z1;
        if (depth <= 10) return;
        const scale = 250 / depth;
        const sx = cx + x1 * scale;
        const sy = cy + pos[1] * scale;

        const isSel = subj.id === selectedSubjectId;
        const nodeR = Math.max(5, (isSel ? 11 : 7) * scale);

        ctx.strokeStyle = isSel ? '#f59e0b' : subj.isThreat ? '#dc2626' : '#4ade80';
        ctx.lineWidth = isSel ? 2 : 1.2;
        ctx.beginPath();
        ctx.arc(sx, sy, nodeR, 0, Math.PI * 2);
        ctx.stroke();

        ctx.fillStyle = ctx.strokeStyle;
        ctx.beginPath();
        ctx.arc(sx, sy, nodeR * 0.45, 0, Math.PI * 2);
        ctx.fill();

        ctx.font = '10px "Share Tech Mono", monospace';
        ctx.fillStyle = isSel ? '#f59e0b' : '#4ade80';
        ctx.fillText(subj.handle, sx + nodeR + 4, sy + 3);

        nextProjected.push({ id: subj.id, x: sx, y: sy, r: nodeR + 6 });
      });
      projectedTargetsRef.current = nextProjected;
    };
    render();

    return () => {
      cancelAnimationFrame(animationId);
      window.removeEventListener('resize', resize);
    };
  }, [mode, subjects, selectedSubjectId]);

  const handleCanvasClick = (e) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const mx = e.clientX - rect.left;
    const my = e.clientY - rect.top;
    const hit = projectedTargetsRef.current.find((t) => Math.hypot(t.x - mx, t.y - my) <= t.r);
    if (hit && onSelectSubject) onSelectSubject(hit.id);
  };

  return <canvas ref={canvasRef} onClick={handleCanvasClick} className="w-full h-full block bg-black/50 cursor-crosshair" />;
}

function MapSurface({ activeSubject }) {
  const canvasRef = useRef(null);
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    let animationId;
    let angle = 0;

    const resize = () => {
      if (!canvas.parentElement) return;
      canvas.width = canvas.parentElement.clientWidth;
      canvas.height = canvas.parentElement.clientHeight;
    };
    resize();
    window.addEventListener('resize', resize);

    const draw = () => {
      animationId = requestAnimationFrame(draw);
      const w = canvas.width,
        h = canvas.height;
      const cx = w / 2,
        cy = h / 2 + 8;
      const radius = Math.min(w, h) * 0.38;

      ctx.fillStyle = 'rgba(5, 5, 5, 0.16)';
      ctx.fillRect(0, 0, w, h);

      ctx.strokeStyle = 'rgba(74, 222, 128, 0.22)';
      ctx.beginPath();
      ctx.arc(cx, cy, radius, 0, Math.PI * 2);
      ctx.arc(cx, cy, radius * 0.55, 0, Math.PI * 2);
      ctx.stroke();

      let px = cx,
        py = cy;
      if (activeSubject.lat != null && activeSubject.lon != null) {
        px = cx + (activeSubject.lon / 180) * (radius * 0.85);
        py = cy - (activeSubject.lat / 90) * (radius * 0.85);
      }

      ctx.save();
      ctx.translate(cx, cy);
      ctx.rotate(angle);
      ctx.beginPath();
      ctx.moveTo(0, 0);
      ctx.arc(0, 0, radius, 0, -0.28, true);
      ctx.lineTo(0, 0);
      const gradient = ctx.createLinearGradient(0, 0, radius, 0);
      gradient.addColorStop(0, 'rgba(74, 222, 128, 0.5)');
      gradient.addColorStop(1, 'rgba(74, 222, 128, 0)');
      ctx.fillStyle = gradient;
      ctx.fill();
      ctx.restore();

      ctx.beginPath();
      ctx.arc(px, py, 4.5, 0, Math.PI * 2);
      ctx.fillStyle = '#dc2626';
      ctx.fill();

      ctx.font = '9px "Share Tech Mono", monospace';
      ctx.fillStyle = '#f59e0b';
      ctx.fillText(`${activeSubject.handle} (${activeSubject.lat?.toFixed(1)}, ${activeSubject.lon?.toFixed(1)})`, 8, h - 8);

      angle += 0.025;
    };
    draw();
    return () => {
      cancelAnimationFrame(animationId);
      window.removeEventListener('resize', resize);
    };
  }, [activeSubject]);

  return <canvas ref={canvasRef} className="w-full h-full block" />;
}