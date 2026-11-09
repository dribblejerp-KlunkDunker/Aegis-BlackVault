package com.example.engine

import com.example.model.*
import java.util.Locale
import kotlin.math.*

/**
 * Catalog of walled-garden platforms and their DOM-drift notes. Scheduled for retirement in roadmap
 * Phase 3 (its recipes rely on private APIs and stealth techniques the platforms prohibit).
 */
object WalledGardenCatalog {
    val WALLED_GARDEN_CATALOG: Map<String, WalledGardenSpec> = linkedMapOf(
        "tiktok" to WalledGardenSpec(
            key = "tiktok",
            name = "TikTok",
            sampleUrl = "https://www.tiktok.com/@Ghost_Protocol_99",
            deprecatedSelector = "div[data-e2e=\"user-post-item-list\"] > div.tiktok-x6y88p",
            mutatedDom = "<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\" type=\"application/json\">{\"__DEFAULT_SCOPE__\":{\"webapp.user-detail\":{\"userInfo\":{\"user\":{\"uniqueId\":\"ghost_protocol_99\"}}}}}</script>",
            healedSelector = "json_hydration://script#__UNIVERSAL_DATA_FOR_REHYDRATION__ -> __DEFAULT_SCOPE__[\"webapp.user-detail\"].userInfo",
            strategy = "Playwright-Stealth + Rehydration State Tree Interceptor",
            pythonSnippet = "async def extract_tiktok_healed(page):\n    raw = await page.locator(\"script#__UNIVERSAL_DATA_FOR_REHYDRATION__\").text_content()\n    scope = json.loads(raw)[\"__DEFAULT_SCOPE__\"][\"webapp.user-detail\"]\n    return scope[\"userInfo\"][\"user\"], scope.get(\"itemList\", [])"
        ),
        "instagram" to WalledGardenSpec(
            key = "instagram",
            name = "Instagram",
            sampleUrl = "https://www.instagram.com/cipher_vane_ops",
            deprecatedSelector = "header section ul li span._ac2a",
            mutatedDom = "<div class=\"x9f619 xjbqb8w x78zum5 x168nmei\" data-bloks-name=\"bk.components.Text\"><!-- React Polaris Obfuscated Tree --></div>",
            healedSelector = "graphql_intercept:///api/v1/users/web_profile_info/?username={handle} [x-ig-app-id: 936619743392459]",
            strategy = "Polaris GraphQL Network Relay + Session Cookie Vault",
            pythonSnippet = "async def extract_instagram_healed(client, handle):\n    headers = {\"x-ig-app-id\": \"936619743392459\", \"User-Agent\": STEALTH_UA}\n    r = await client.get(f\"https://www.instagram.com/api/v1/users/web_profile_info/?username={handle}\", headers=headers)\n    return r.json()[\"data\"][\"user\"]"
        ),
        "youtube" to WalledGardenSpec(
            key = "youtube",
            name = "YouTube",
            sampleUrl = "https://www.youtube.com/@NullRouterBroadcast",
            deprecatedSelector = "ytd-channel-about-metadata-renderer #description-container",
            mutatedDom = "<script nonce=\"a8F9kL\">var ytInitialData = {\"header\":{\"pageHeaderRenderer\":{\"content\":{\"pageHeaderViewModel\":{\"title\":{\"dynamicTextViewModel\":{\"text\":{\"content\":\"NullRouterBroadcast\"}}}}}}}};</script>",
            healedSelector = "ast_script_var://window.ytInitialData.header.pageHeaderRenderer.content.pageHeaderViewModel",
            strategy = "AST Script Variable Parser (ytInitialData + Whisper Transcript Pipe)",
            pythonSnippet = "def extract_youtube_healed(html_body: str):\n    m = re.search(r\"var ytInitialData\\s*=\\s*(\\{.*?\\});</script>\", html_body, re.S)\n    data = json.loads(m.group(1))\n    return data[\"header\"][\"pageHeaderRenderer\"][\"content\"][\"pageHeaderViewModel\"]"
        ),
        "reddit" to WalledGardenSpec(
            key = "reddit",
            name = "Reddit",
            sampleUrl = "https://www.reddit.com/user/ZeroTrustSaboteur",
            deprecatedSelector = "div.Post[data-testid=\"post-container\"] div.RichTextJSON-root",
            mutatedDom = "<shreddit-post permalink=\"/r/netsec/comments/1x9a/\" author=\"ZeroTrustSaboteur\" created-timestamp=\"2026-10-18T03:14:00Z\">",
            healedSelector = "shadow_dom://shreddit-post[@author] ::shadow-root(open) + /user/{handle}/submitted.json?raw_json=1",
            strategy = "Shreddit WebComponent Shadow-DOM Piercer + JSON Endpoint Failover",
            pythonSnippet = "async def extract_reddit_healed(page, handle):\n    posts = await page.locator(\"shreddit-post\").evaluate_all(\n        \"els => els.map(e => ({author: e.getAttribute('author'), ts: e.getAttribute('created-timestamp'), text: e.innerText}))\"\n    )\n    return posts"
        ),
        "facebook" to WalledGardenSpec(
            key = "facebook",
            name = "Facebook",
            sampleUrl = "https://www.facebook.com/groups/sovereign.mesh.ops",
            deprecatedSelector = "div[role=\"feed\"] div[data-ad-preview=\"message\"]",
            mutatedDom = "<script type=\"application/json\" data-sjs>{\"require\":[[\"ScheduledServerJS\",\"handle\",null,[{\"__bbox\":{\"result\":{\"data\":{\"group\":{\"feed_units\":{}}}}}}]]]}</script>",
            healedSelector = "relay_bbox://script[type=\"application/json\"][data-sjs] -> ScheduledServerJS.__bbox.result.data",
            strategy = "Relay Comet ScheduledServerJS BBox JSON Stream Reconstructor",
            pythonSnippet = "def extract_facebook_bbox(html_body: str):\n    blobs = re.findall(r'<script type=\"application/json\" data-sjs>(.*?)</script>', html_body)\n    return [json.loads(b) for b in blobs if \"__bbox\" in b]"
        ),
        "onlyfans" to WalledGardenSpec(
            key = "onlyfans",
            name = "OnlyFans",
            sampleUrl = "https://onlyfans.com/velvet_opsec_vip",
            deprecatedSelector = "div.b-profile__header div.b-user-info__detail",
            mutatedDom = "<div class=\"g-user-realname__wrapper\" data-v-68e32a14><!-- Dynamic X-BC + SHA1 Dynamic Rules Checksum Required --></div>",
            healedSelector = "signed_xhr:///api2/v2/users/{handle} [sign: sha1(static_param + timestamp + path + user_id)]",
            strategy = "Dynamic SHA-1 Request Signer + Residential Mobile Proxy Rotation",
            pythonSnippet = "def sign_onlyfans_request(path: str, ts: int, static_param: str, checksum_indexes: list):\n    msg = \"\\n\".join([static_param, str(ts), path, \"0\"])\n    sha = hashlib.sha1(msg.encode()).hexdigest()\n    checksum = sum(ord(sha[i]) for i in checksum_indexes) + 109\n    return f\"614:{sha}:{checksum:x}:65a8b\""
        )
    )

    fun detectWalledGarden(uri: String): WalledGardenSpec? {
        val lower = uri.lowercase(Locale.US)
        return when {
            "tiktok" in lower -> WALLED_GARDEN_CATALOG["tiktok"]
            "instagram" in lower || "ig.me" in lower -> WALLED_GARDEN_CATALOG["instagram"]
            "youtube" in lower || "youtu.be" in lower -> WALLED_GARDEN_CATALOG["youtube"]
            "reddit" in lower -> WALLED_GARDEN_CATALOG["reddit"]
            "facebook" in lower || "fb.com" in lower -> WALLED_GARDEN_CATALOG["facebook"]
            "onlyfans" in lower -> WALLED_GARDEN_CATALOG["onlyfans"]
            else -> null
        }
    }
}
