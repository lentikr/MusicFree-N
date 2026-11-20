package com.musicfree.demo.plugin.runtime

/**
 * 注入 QuickJS 的 MusicFree 兼容运行时脚本：
 * - 基础 Polyfill: globalThis / atob / btoa / Buffer / URLSearchParams / console
 * - 白名单 require(): axios, cheerio, crypto-js, qs, he, dayjs, big-integer, @react-native-cookies/cookies
 * - CommonJS 插件加载器与 IPluginDefine 方法调用封装
 */
object MusicFreeSandboxBootstrap {
    val SCRIPT: String = """
(function () {
  const g = globalThis;
  g.__mf_plugins = {};
  g.__mf_userVars = {};

  // ---- Console bridge ----
  g.console = {
    log: (...args) => nativeConsole.log(args.map(stringifyArg).join(" ")),
    info: (...args) => nativeConsole.log(args.map(stringifyArg).join(" ")),
    warn: (...args) => nativeConsole.warn(args.map(stringifyArg).join(" ")),
    error: (...args) => nativeConsole.error(args.map(stringifyArg).join(" "))
  };

  function stringifyArg(v) {
    if (v === null) return "null";
    if (v === undefined) return "undefined";
    if (typeof v === "object") {
      try { return JSON.stringify(v); } catch (_) { return String(v); }
    }
    return String(v);
  }

  // ---- Base64 & Buffer polyfill ----
  g.btoa = (str) => nativeCrypto.base64Encode(String(str));
  g.atob = (str) => nativeCrypto.base64Decode(String(str));

  function SimpleBuffer(str) {
    this._str = str;
  }
  SimpleBuffer.from = function (input, encoding) {
    if (encoding === "base64") {
      return new SimpleBuffer(nativeCrypto.base64Decode(String(input)));
    }
    if (encoding === "hex") {
      const hex = String(input);
      let out = "";
      for (let i = 0; i < hex.length; i += 2) {
        out += String.fromCharCode(parseInt(hex.substr(i, 2), 16));
      }
      return new SimpleBuffer(out);
    }
    return new SimpleBuffer(String(input));
  };
  SimpleBuffer.prototype.toString = function (encoding) {
    if (encoding === "base64") return nativeCrypto.base64Encode(this._str);
    if (encoding === "hex") {
      let hex = "";
      for (let i = 0; i < this._str.length; i++) {
        hex += this._str.charCodeAt(i).toString(16).padStart(2, "0");
      }
      return hex;
    }
    return this._str;
  };
  g.Buffer = SimpleBuffer;

  // ---- qs shim ----
  const qsShim = {
    stringify(obj) {
      if (!obj || typeof obj !== "object") return "";
      return Object.keys(obj)
        .filter((k) => obj[k] !== undefined && obj[k] !== null)
        .map((k) => encodeURIComponent(k) + "=" + encodeURIComponent(String(obj[k])))
        .join("&");
    },
    parse(str) {
      const out = {};
      if (!str) return out;
      String(str).replace(/^\?/, "").split("&").forEach((pair) => {
        if (!pair) return;
        const idx = pair.indexOf("=");
        const k = idx >= 0 ? decodeURIComponent(pair.slice(0, idx)) : decodeURIComponent(pair);
        const v = idx >= 0 ? decodeURIComponent(pair.slice(idx + 1)) : "";
        out[k] = v;
      });
      return out;
    }
  };

  // ---- he (HTML entities) shim ----
  const heShim = {
    decode(str) {
      return String(str || "")
        .replace(/&amp;/g, "&")
        .replace(/&lt;/g, "<")
        .replace(/&gt;/g, ">")
        .replace(/&quot;/g, '"')
        .replace(/&#39;|&apos;/g, "'")
        .replace(/&nbsp;/g, " ")
        .replace(/&#(\d+);/g, (_, d) => String.fromCharCode(Number(d)))
        .replace(/&#x([0-9a-fA-F]+);/g, (_, h) => String.fromCharCode(parseInt(h, 16)));
    },
    encode(str) {
      return String(str || "")
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;");
    }
  };

  // ---- dayjs lightweight shim ----
  function dayjsShim(input) {
    const d = input ? new Date(input) : new Date();
    return {
      unix: () => Math.floor(d.getTime() / 1000),
      valueOf: () => d.getTime(),
      format(fmt) {
        const pad = (n) => String(n).padStart(2, "0");
        return String(fmt || "YYYY-MM-DD HH:mm:ss")
          .replace("YYYY", String(d.getFullYear()))
          .replace("MM", pad(d.getMonth() + 1))
          .replace("DD", pad(d.getDate()))
          .replace("HH", pad(d.getHours()))
          .replace("mm", pad(d.getMinutes()))
          .replace("ss", pad(d.getSeconds()));
      }
    };
  }
  dayjsShim.unix = (sec) => dayjsShim(Number(sec) * 1000);

  // ---- big-integer shim (backed by ES2020 BigInt) ----
  function bigIntShim(value, radix) {
    const v = radix ? BigInt(parseInt(String(value), radix)) : BigInt(value);
    const wrap = (n) => ({
      value: n,
      add: (o) => wrap(n + BigInt(o.value ?? o)),
      subtract: (o) => wrap(n - BigInt(o.value ?? o)),
      multiply: (o) => wrap(n * BigInt(o.value ?? o)),
      divide: (o) => wrap(n / BigInt(o.value ?? o)),
      mod: (o) => wrap(n % BigInt(o.value ?? o)),
      toString: (r) => n.toString(r || 10)
    });
    return wrap(v);
  }

  // ---- crypto-js shim (bridged to Kotlin MessageDigest / Mac / Cipher) ----
  function WordValue(raw, isHex) {
    this._raw = String(raw);
    this._isHex = !!isHex;
  }
  WordValue.prototype.toString = function (encoder) {
    if (encoder && encoder._type === "base64") {
      return nativeCrypto.base64Encode(this._raw);
    }
    return this._raw;
  };
  const cryptoJsShim = {
    enc: {
      Utf8: { _type: "utf8", parse: (s) => new WordValue(s, false), stringify: (w) => String(w._raw ?? w) },
      Base64: { _type: "base64", parse: (s) => new WordValue(nativeCrypto.base64Decode(String(s)), false), stringify: (w) => nativeCrypto.base64Encode(String(w._raw ?? w)) },
      Hex: { _type: "hex", parse: (s) => new WordValue(s, true), stringify: (w) => String(w._raw ?? w) }
    },
    mode: { CBC: "CBC", ECB: "ECB" },
    pad: { Pkcs7: "Pkcs7" },
    MD5: (s) => new WordValue(nativeCrypto.hashHex("MD5", String(s._raw ?? s)), true),
    SHA1: (s) => new WordValue(nativeCrypto.hashHex("SHA-1", String(s._raw ?? s)), true),
    SHA256: (s) => new WordValue(nativeCrypto.hashHex("SHA-256", String(s._raw ?? s)), true),
    HmacSHA1: (s, k) => new WordValue(nativeCrypto.hmacHex("HmacSHA1", String(s._raw ?? s), String(k._raw ?? k)), true),
    HmacSHA256: (s, k) => new WordValue(nativeCrypto.hmacHex("HmacSHA256", String(s._raw ?? s), String(k._raw ?? k)), true),
    AES: {
      encrypt(msg, key, cfg) {
        const mode = (cfg && cfg.mode) || "CBC";
        const iv = (cfg && cfg.iv && (cfg.iv._raw || cfg.iv)) || "0000000000000000";
        const b64 = nativeCrypto.aesEncryptBase64(String(msg._raw ?? msg), String(key._raw ?? key), String(iv), mode);
        return { toString: () => b64, ciphertext: new WordValue(b64, false) };
      },
      decrypt(cipher, key, cfg) {
        const mode = (cfg && cfg.mode) || "CBC";
        const iv = (cfg && cfg.iv && (cfg.iv._raw || cfg.iv)) || "0000000000000000";
        const txt = nativeCrypto.aesDecryptUtf8(String(cipher._raw ?? cipher), String(key._raw ?? key), String(iv), mode);
        return new WordValue(txt, false);
      }
    }
  };

  // ---- cheerio shim (bridged to Kotlin Jsoup) ----
  function makeCheerioSelection(nodes) {
    const sel = {
      length: nodes.length,
      text: () => nodes.map((n) => n.text).join(""),
      html: () => (nodes[0] ? nodes[0].html : null),
      attr: (name) => (nodes[0] && nodes[0].attrs ? nodes[0].attrs[name] : undefined),
      first: () => makeCheerioSelection(nodes.slice(0, 1)),
      eq: (i) => makeCheerioSelection(nodes[i] ? [nodes[i]] : []),
      each: (cb) => {
        nodes.forEach((n, idx) => cb(idx, n));
        return sel;
      },
      map: (cb) => ({
        get: () => nodes.map((n, idx) => cb(idx, n))
      }),
      find: (subSelector) => {
        const merged = [];
        nodes.forEach((n) => {
          const found = JSON.parse(nativeHtml.select(n.html || "", subSelector));
          merged.push(...found);
        });
        return makeCheerioSelection(merged);
      }
    };
    return sel;
  }

  const cheerioShim = {
    load(html) {
      const sourceHtml = String(html || "");
      const $ = function (selector) {
        if (typeof selector === "object" && selector !== null && "attrs" in selector) {
          return makeCheerioSelection([selector]);
        }
        const nodes = JSON.parse(nativeHtml.select(sourceHtml, String(selector)));
        return makeCheerioSelection(nodes);
      };
      $.html = () => sourceHtml;
      return $;
    }
  };

  // ---- axios shim (bridged to Kotlin OkHttp via asyncFunction) ----
  function createAxiosInstance(defaultCfg) {
    const defaults = Object.assign({ headers: {} }, defaultCfg || {});

    async function axiosRequest(urlOrConfig, maybeConfig) {
      const cfg = typeof urlOrConfig === "string"
        ? Object.assign({ url: urlOrConfig }, maybeConfig || {})
        : Object.assign({}, urlOrConfig || {});

      const baseURL = cfg.baseURL || defaults.baseURL || "";
      const fullUrl = (baseURL && !/^https?:\/\//i.test(cfg.url || ""))
        ? baseURL.replace(/\/$/, "") + "/" + String(cfg.url || "").replace(/^\//, "")
        : (cfg.url || "");

      const headers = Object.assign({}, defaults.headers || {}, cfg.headers || {});
      const payload = {
        url: fullUrl,
        method: (cfg.method || "GET").toUpperCase(),
        headers,
        params: cfg.params || null,
        data: cfg.data !== undefined ? cfg.data : null,
        responseType: cfg.responseType || "json",
        timeout: cfg.timeout || defaults.timeout || 15000
      };

      const rawResp = await nativeHttp.request(JSON.stringify(payload));
      const resp = JSON.parse(rawResp);
      let parsedData = resp.data;
      if (!resp.isBase64 && payload.responseType !== "text") {
        try {
          parsedData = JSON.parse(resp.data);
        } catch (_) {
          parsedData = resp.data;
        }
      }
      return {
        status: resp.status,
        statusText: resp.statusText,
        headers: resp.headers,
        data: parsedData,
        config: cfg
      };
    }

    axiosRequest.defaults = defaults;
    axiosRequest.request = (cfg) => axiosRequest(cfg);
    axiosRequest.get = (url, cfg) => axiosRequest(Object.assign({}, cfg, { url, method: "GET" }));
    axiosRequest.delete = (url, cfg) => axiosRequest(Object.assign({}, cfg, { url, method: "DELETE" }));
    axiosRequest.head = (url, cfg) => axiosRequest(Object.assign({}, cfg, { url, method: "HEAD" }));
    axiosRequest.post = (url, data, cfg) => axiosRequest(Object.assign({}, cfg, { url, data, method: "POST" }));
    axiosRequest.put = (url, data, cfg) => axiosRequest(Object.assign({}, cfg, { url, data, method: "PUT" }));
    axiosRequest.create = (c) => createAxiosInstance(c);
    axiosRequest.default = axiosRequest;
    return axiosRequest;
  }

  const axiosShim = createAxiosInstance({});

  // ---- @react-native-cookies/cookies shim ----
  const cookieShim = {
    async get(url) {
      return JSON.parse(nativeCookie.get(String(url)));
    },
    async set(url, cookie) {
      nativeCookie.set(String(url), String(cookie.name || ""), String(cookie.value || ""));
      return true;
    },
    async clearAll() {
      nativeCookie.clearAll();
      return true;
    }
  };

  // ---- MusicFree whitelist require() ----
  const PACKAGES = {
    "axios": axiosShim,
    "cheerio": cheerioShim,
    "crypto-js": cryptoJsShim,
    "qs": qsShim,
    "he": heShim,
    "dayjs": dayjsShim,
    "big-integer": bigIntShim,
    "@react-native-cookies/cookies": cookieShim
  };

  function mfRequire(name) {
    if (Object.prototype.hasOwnProperty.call(PACKAGES, name)) {
      return PACKAGES[name];
    }
    throw new Error("MusicFree 沙箱暂未内置模块: " + name);
  }

  // ---- Plugin loader & protocol invokers ----
  const PROTOCOL_METHODS = [
    "search",
    "getMediaSource",
    "getLyric",
    "getAlbumInfo",
    "getMusicSheetInfo",
    "getArtistWorks",
    "importMusicSheet",
    "importMusicItem",
    "getTopLists",
    "getTopListDetail",
    "getRecommendSheetTags",
    "getRecommendSheetsByTag",
    "getMusicInfo"
  ];

  g.__mf_loadPlugin = function (pluginId, sourceCode, fallbackUrl) {
    const module = { exports: {} };
    const env = {
      os: "android",
      appVersion: "0.6.0",
      getUserVariables: () => g.__mf_userVars[pluginId] || {}
    };
    const wrapper = new Function("module", "exports", "require", "env", "console", sourceCode);
    wrapper(module, module.exports, mfRequire, env, g.console);
    const instance = module.exports && module.exports.default ? module.exports.default : module.exports;
    if (!instance || !instance.platform) {
      throw new Error("无效的 MusicFree 插件：module.exports 缺少 platform 字段");
    }
    g.__mf_plugins[pluginId] = instance;

    const supportedMethods = PROTOCOL_METHODS.filter((m) => typeof instance[m] === "function");
    return JSON.stringify({
      id: pluginId,
      platform: String(instance.platform),
      version: String(instance.version || "0.0.0"),
      author: String(instance.author || ""),
      srcUrl: String(instance.srcUrl || fallbackUrl || ""),
      defaultSearchType: String(instance.defaultSearchType || "music"),
      supportedSearchType: Array.isArray(instance.supportedSearchType) ? instance.supportedSearchType : ["music"],
      supportedMethods,
      userVariables: Array.isArray(instance.userVariables) ? instance.userVariables : []
    });
  };

  g.__mf_unloadPlugin = function (pluginId) {
    delete g.__mf_plugins[pluginId];
    return "ok";
  };

  g.__mf_search = async function (pluginId, query, page, type) {
    const plugin = g.__mf_plugins[pluginId];
    if (!plugin || typeof plugin.search !== "function") {
      throw new Error("该插件未实现 search() 接口");
    }
    const res = await plugin.search(query, page, type);
    return JSON.stringify(res || { isEnd: true, data: [] });
  };

  g.__mf_getMediaSource = async function (pluginId, musicItemJson, quality) {
    const plugin = g.__mf_plugins[pluginId];
    if (!plugin || typeof plugin.getMediaSource !== "function") {
      throw new Error("该插件未实现 getMediaSource() 接口");
    }
    const item = JSON.parse(musicItemJson);
    const res = await plugin.getMediaSource(item, quality || "standard");
    return JSON.stringify(res || {});
  };

  g.__mf_getLyric = async function (pluginId, musicItemJson) {
    const plugin = g.__mf_plugins[pluginId];
    if (!plugin || typeof plugin.getLyric !== "function") {
      throw new Error("该插件未实现 getLyric() 接口");
    }
    const item = JSON.parse(musicItemJson);
    const res = await plugin.getLyric(item);
    return JSON.stringify(res || {});
  };

  g.__mf_getTopLists = async function (pluginId) {
    const plugin = g.__mf_plugins[pluginId];
    if (!plugin || typeof plugin.getTopLists !== "function") {
      throw new Error("该插件未实现 getTopLists() 接口");
    }
    const res = await plugin.getTopLists();
    return JSON.stringify(res || []);
  };

  return "bootstrap_ready";
})();
""".trimIndent()
}
