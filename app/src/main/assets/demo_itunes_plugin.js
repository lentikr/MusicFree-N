/**
 * 内置 MusicFree 协议验证插件（基于 Apple iTunes 公开搜索接口）
 * 用途：零配置验证 CommonJS 沙箱、require("axios") -> OkHttp 桥接、
 *       search()、getMediaSource()、getLyric() 以及 Media3 音频播放全链路。
 */
const axios = require("axios");
const dayjs = require("dayjs");

module.exports = {
  platform: "iTunes Demo (内置验证源)",
  version: "1.0.0",
  author: "MusicFreeNativeDemo",
  srcUrl: "asset://demo_itunes_plugin.js",
  primaryKey: ["id"],
  defaultSearchType: "music",
  supportedSearchType: ["music", "album"],

  async search(query, page, type) {
    const limit = 15;
    const offset = (Math.max(1, page || 1) - 1) * limit;
    const entity = type === "album" ? "album" : "song";
    const res = await axios.get("https://itunes.apple.com/search", {
      params: {
        term: query,
        media: "music",
        entity,
        limit,
        offset,
        country: "US"
      },
      headers: {
        "User-Agent": "MusicFreeNativeDemo/0.1"
      }
    });

    const body = typeof res.data === "string" ? JSON.parse(res.data) : res.data;
    const items = (body && body.results) || [];

    const data = items.map((item) => ({
      id: String(item.trackId || item.collectionId || Math.random()),
      title: item.trackName || item.collectionName || "Unknown",
      artist: item.artistName || "Unknown Artist",
      album: item.collectionName || "",
      artwork: (item.artworkUrl100 || "").replace("100x100", "300x300"),
      duration: item.trackTimeMillis ? Math.round(item.trackTimeMillis / 1000) : 0,
      previewUrl: item.previewUrl || "",
      releaseDate: item.releaseDate ? dayjs(item.releaseDate).format("YYYY-MM-DD") : ""
    }));

    return {
      isEnd: items.length < limit,
      data
    };
  },

  async getMediaSource(musicItem, quality) {
    if (!musicItem || !musicItem.previewUrl) {
      throw new Error("该条目没有可试听的 previewUrl");
    }
    return {
      url: musicItem.previewUrl,
      headers: {
        "User-Agent": "MusicFreeNativeDemo/0.1"
      },
      quality: quality || "standard"
    };
  },

  async getLyric(musicItem) {
    const title = (musicItem && musicItem.title) || "Unknown";
    const artist = (musicItem && musicItem.artist) || "Unknown";
    return {
      rawLrc: `[00:00.00]${title} - ${artist}\n[00:02.00]本插件用于验证 MusicFree 协议 getLyric() 接口连通性\n[00:05.00]已成功通过 QuickJS 沙箱返回 LRC 歌词结构`
    };
  }
};
