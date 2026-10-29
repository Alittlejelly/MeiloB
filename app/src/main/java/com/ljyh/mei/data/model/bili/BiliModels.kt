package com.ljyh.mei.data.model.bili

import com.google.gson.annotations.SerializedName

/** 音乐区 rid（B 站分区 ID）。 */
const val BILI_MUSIC_RID = 3

/**
 * 首页可切换的 B 站一级分区（rid 即官方分区表的 tid）。
 * 顺序即界面展示顺序，音乐区为默认。
 */
data class BiliRegion(val rid: Int, val label: String)

val BILI_HOME_REGIONS = listOf(
    BiliRegion(BILI_MUSIC_RID, "音乐"),
    BiliRegion(1, "动画"),
    BiliRegion(129, "舞蹈"),
    BiliRegion(4, "游戏"),
    BiliRegion(36, "知识"),
    BiliRegion(188, "科技"),
    BiliRegion(234, "运动"),
    BiliRegion(5, "娱乐"),
    BiliRegion(160, "生活"),
    BiliRegion(211, "美食"),
    BiliRegion(119, "鬼畜"),
)

/** B 站音频流 ID 与音质的对应关系。 */
const val BILI_AUDIO_ID_HIRES = 30280
const val BILI_AUDIO_ID_EXHIGH = 30232
const val BILI_AUDIO_ID_STANDARD = 30216

data class BiliCodeResponse(
    @SerializedName("code") val code: Int = 0,
    @SerializedName("message") val message: String? = null,
)

data class BiliQrGenerate(
    val code: Int = 0,
    val message: String? = null,
    val data: Data? = null,
) {
    data class Data(
        val url: String? = null,
        @SerializedName("qrcode_key") val qrcodeKey: String? = null,
    )
}

data class BiliQrPoll(
    val code: Int = 0,
    val message: String? = null,
    val data: Data? = null,
) {
    data class Data(
        val url: String? = null,
        @SerializedName("refresh_token") val refreshToken: String? = null,
        val timestamp: Long = 0,
        val code: Int = 0,
        val message: String? = null,
    )
}

data class BiliNav(
    val code: Int = 0,
    val message: String? = null,
    val data: Data? = null,
) {
    data class Data(
        @SerializedName("isLogin") val isLogin: Boolean = false,
        @SerializedName("mid") val mid: Long = 0,
        @SerializedName("uname") val uname: String? = null,
        @SerializedName("face") val face: String? = null,
        @SerializedName("money") val money: Double = 0.0,
        @SerializedName("coin") val coin: Double = 0.0,
        @SerializedName("vipStatus") val vipStatus: Int = 0,
        @SerializedName(value = "level_info", alternate = ["levelInfo"]) val levelInfo: LevelInfo? = null,
        @SerializedName("wbi_img") val wbiImg: WbiImg? = null,
    ) {
        data class LevelInfo(
            @SerializedName("current_level") val currentLevel: Int = 0,
        )

        data class WbiImg(
            @SerializedName("img_url") val imgUrl: String? = null,
            @SerializedName("sub_url") val subUrl: String? = null,
        )
    }
}

data class BiliCaptcha(
    val code: Int = 0,
    val message: String? = null,
    val data: Data? = null,
) {
    data class Data(
        val type: String? = null,
        val token: String? = null,
        @SerializedName("geetest") val geetest: Geetest? = null,
    ) {
        data class Geetest(
            val gt: String? = null,
            val challenge: String? = null,
        )
    }
}

data class BiliSmsSend(
    val code: Int = 0,
    val message: String? = null,
    val data: Data? = null,
) {
    data class Data(
        @SerializedName("captcha_key") val captchaKey: String? = null,
        @SerializedName("captcha_state") val captchaState: String? = null,
        @SerializedName("recaptcha_url") val recaptchaUrl: String? = null,
    )
}

data class BiliSmsLogin(
    val code: Int = 0,
    val message: String? = null,
    val data: Data? = null,
) {
    data class Data(
        val url: String? = null,
        @SerializedName("refresh_token") val refreshToken: String? = null,
        val timestamp: Long = 0,
        @SerializedName("mid") val mid: Long = 0,
        @SerializedName("is_new_user") val isNewUser: Boolean = false,
    )
}

data class BiliFingerSpi(
    val code: Int = 0,
    val data: Data? = null,
) {
    data class Data(
        @SerializedName("b_3") val b3: String? = null,
        @SerializedName("b_4") val b4: String? = null,
    )
}

/** 分区动态 / 排行榜通用的视频条目。 */
data class BiliArchive(
    @SerializedName("aid") val aid: Long = 0,
    @SerializedName("bvid") val bvid: String? = null,
    @SerializedName("cid") val cid: Long = 0,
    @SerializedName("title") val title: String? = null,
    @SerializedName("desc") val desc: String? = null,
    @SerializedName("pic") val pic: String? = null,
    /** 秒 */
    @SerializedName("duration") val duration: Int = 0,
    @SerializedName("pubdate") val pubdate: Long = 0,
    @SerializedName("owner") val owner: Owner? = null,
    @SerializedName("stat") val stat: Stat? = null,
    @SerializedName("upper") val upper: Upper? = null,
    @SerializedName("cnt_info") val cntInfo: CntInfo? = null,
    @SerializedName("score") val score: Long = 0,
    @SerializedName("tname") val tname: String? = null,
) {
    val uploaderName: String?
        get() = owner?.name ?: upper?.name

    val uploaderMid: Long
        get() = owner?.mid ?: upper?.mid ?: 0

    val playCount: Long
        get() = stat?.view ?: cntInfo?.play ?: 0

    val danmakuCount: Long
        get() = stat?.danmaku ?: cntInfo?.danmaku ?: 0

    val likeCount: Long
        get() = stat?.like ?: 0

    val favoriteCount: Long
        get() = stat?.favorite ?: cntInfo?.collect ?: 0

    data class Owner(
        @SerializedName("mid") val mid: Long = 0,
        @SerializedName("name") val name: String? = null,
        @SerializedName("face") val face: String? = null,
    )

    data class Stat(
        @SerializedName("view") val view: Long = 0,
        @SerializedName("danmaku") val danmaku: Long = 0,
        @SerializedName("favorite") val favorite: Long = 0,
        @SerializedName("like") val like: Long = 0,
    )

    data class Upper(
        @SerializedName("mid") val mid: Long = 0,
        @SerializedName("name") val name: String? = null,
        @SerializedName("face") val face: String? = null,
    )

    data class CntInfo(
        @SerializedName("collect") val collect: Long = 0,
        @SerializedName("play") val play: Long = 0,
        @SerializedName("danmaku") val danmaku: Long = 0,
    )
}

data class BiliRegionDynamic(
    val code: Int = 0,
    val message: String? = null,
    val data: Data? = null,
) {
    data class Data(
        @SerializedName("archives") val archives: List<BiliArchive>? = null,
        @SerializedName("page") val page: Page? = null,
    ) {
        data class Page(
            @SerializedName("num") val num: Int = 0,
            @SerializedName("size") val size: Int = 0,
            @SerializedName("count") val count: Int = 0,
        )
    }
}

data class BiliRanking(
    val code: Int = 0,
    val message: String? = null,
    val data: Data? = null,
) {
    data class Data(
        @SerializedName("list") val list: List<BiliArchive>? = null,
    )
}

data class BiliArchiveStat(
    val code: Int = 0,
    val message: String? = null,
    val data: Data? = null,
) {
    data class Data(
        @SerializedName("aid") val aid: Long = 0,
        @SerializedName("bvid") val bvid: String? = null,
        @SerializedName("view") val view: Long = 0,
    )
}

data class BiliVideoDetail(
    val code: Int = 0,
    val message: String? = null,
    val data: Data? = null,
) {
    data class Data(
        @SerializedName("bvid") val bvid: String? = null,
        @SerializedName("aid") val aid: Long = 0,
        @SerializedName("cid") val cid: Long = 0,
        @SerializedName("title") val title: String? = null,
        @SerializedName("pic") val pic: String? = null,
        /** 秒 */
        @SerializedName("duration") val duration: Int = 0,
        @SerializedName("owner") val owner: BiliArchive.Owner? = null,
        @SerializedName("stat") val stat: BiliArchive.Stat? = null,
        @SerializedName("pages") val pages: List<Page>? = null,
    ) {
        data class Page(
            @SerializedName("cid") val cid: Long = 0,
            @SerializedName("part") val part: String? = null,
            /** 秒 */
            @SerializedName("duration") val duration: Int = 0,
        )
    }
}

data class BiliPlayUrl(
    val code: Int = 0,
    val message: String? = null,
    val data: Data? = null,
) {
    data class Data(
        @SerializedName("accept_quality") val acceptQuality: List<Int>? = null,
        @SerializedName("quality") val quality: Int = 0,
        @SerializedName("dash") val dash: Dash? = null,
    ) {
        data class Dash(
            @SerializedName("duration") val duration: Int = 0,
            @SerializedName("audio") val audio: List<Stream>? = null,
            @SerializedName("flac") val flac: Stream? = null,
            @SerializedName("dolby") val dolby: Dolby? = null,
        ) {
            data class Stream(
                @SerializedName("id") val id: Int = 0,
                @SerializedName("base_url") val baseUrl: String? = null,
                @SerializedName("baseUrl") val baseUrlCamel: String? = null,
                @SerializedName("bandwidth") val bandwidth: Long = 0,
                @SerializedName("mimeType") val mimeType: String? = null,
                @SerializedName("mime_type") val mimeTypeSnake: String? = null,
                @SerializedName("codecs") val codecs: String? = null,
                @SerializedName("size") val size: Long = 0,
            ) {
                val url: String?
                    get() = baseUrl ?: baseUrlCamel

                val mime: String?
                    get() = mimeType ?: mimeTypeSnake
            }

            data class Dolby(
                @SerializedName("type") val type: Int = 0,
                @SerializedName("audio") val audio: List<Stream>? = null,
            )
        }
    }
}

data class BiliFavFolderListAll(
    val code: Int = 0,
    val message: String? = null,
    val data: Data? = null,
) {
    data class Data(
        @SerializedName("count") val count: Int = 0,
        @SerializedName("list") val list: List<Folder>? = null,
    ) {
        data class Folder(
            @SerializedName("id") val id: Long = 0,
            @SerializedName("fid") val fid: Long = 0,
            @SerializedName("title") val title: String? = null,
            @SerializedName("media_count") val mediaCount: Int = 0,
            /** 属性位：bit0 私有收藏夹（0 公开 / 1 私有），bit1 是否默认收藏夹。 */
            @SerializedName("attr") val attr: Int = 0,
        )
    }
}

/** 收藏的合集 / 收藏夹列表（collected/list）。 */
data class BiliFavFolderCollected(
    val code: Int = 0,
    val message: String? = null,
    val data: Data? = null,
) {
    data class Data(
        @SerializedName("count") val count: Int = 0,
        @SerializedName("has_more") val hasMore: Boolean = false,
        @SerializedName("list") val list: List<Folder>? = null,
    ) {
        data class Folder(
            @SerializedName("id") val id: Long = 0,
            @SerializedName("fid") val fid: Long = 0,
            @SerializedName("title") val title: String? = null,
            @SerializedName("media_count") val mediaCount: Int = 0,
            @SerializedName("upper") val upper: BiliArchive.Upper? = null,
        )
    }
}

data class BiliFavResourceList(
    val code: Int = 0,
    val message: String? = null,
    val data: Data? = null,
) {
    data class Data(
        @SerializedName("info") val info: Info? = null,
        @SerializedName("medias") val medias: List<Media>? = null,
        @SerializedName("has_more") val hasMore: Boolean = false,
    ) {
        data class Info(
            @SerializedName("id") val id: Long = 0,
            @SerializedName("title") val title: String? = null,
            @SerializedName("media_count") val mediaCount: Int = 0,
            @SerializedName("upper") val upper: BiliArchive.Upper? = null,
        )

        data class Media(
            @SerializedName("id") val id: Long = 0,
            @SerializedName("bvid") val bvid: String? = null,
            @SerializedName("cid") val cid: Long = 0,
            @SerializedName("type") val type: Int = 0,
            @SerializedName("title") val title: String? = null,
            @SerializedName("cover") val cover: String? = null,
            /** 秒 */
            @SerializedName("duration") val duration: Int = 0,
            @SerializedName("upper") val upper: BiliArchive.Upper? = null,
            @SerializedName("cnt_info") val cntInfo: BiliArchive.CntInfo? = null,
            @SerializedName("intro") val intro: String? = null,
            /** bit0 为 1 表示内容已失效（删除/不可见）。 */
            @SerializedName("attr") val attr: Int = 0,
        )
    }
}

data class BiliSearchAll(
    val code: Int = 0,
    val message: String? = null,
    val data: Data? = null,
) {
    data class Data(
        @SerializedName("result") val result: List<Block>? = null,
    ) {
        data class Block(
            @SerializedName("result_type") val resultType: String? = null,
            @SerializedName("data") val data: List<Item>? = null,
        ) {
            data class Item(
                @SerializedName("aid") val aid: Long = 0,
                @SerializedName("bvid") val bvid: String? = null,
                @SerializedName("author") val author: String? = null,
                @SerializedName("mid") val mid: Long = 0,
                @SerializedName("title") val title: String? = null,
                @SerializedName("description") val description: String? = null,
                @SerializedName("pic") val pic: String? = null,
                /** "MM:SS" 或 "HH:MM:SS" */
                @SerializedName("duration") val duration: String? = null,
                @SerializedName("play") val play: Long = 0,
                @SerializedName("video_review") val videoReview: Long = 0,
                @SerializedName("danmaku") val danmaku: Long = 0,
                @SerializedName("pubdate") val pubdate: Long = 0,
                @SerializedName("type") val type: String? = null,
            )
        }
    }
}

/** UP 主空间名片（x/web-interface/card）。 */
data class BiliSpaceCard(
    val code: Int = 0,
    val message: String? = null,
    val data: Data? = null,
) {
    data class Data(
        @SerializedName("card") val card: Card? = null,
        @SerializedName("follower") val follower: Long = 0,
    ) {
        data class Card(
            @SerializedName("mid") val mid: Long = 0,
            @SerializedName("name") val name: String? = null,
            @SerializedName("face") val face: String? = null,
            @SerializedName("sign") val sign: String? = null,
            @SerializedName("official_verify") val officialVerify: OfficialVerify? = null,
        ) {
            data class OfficialVerify(
                @SerializedName("type") val type: Int = -1,
                @SerializedName("desc") val desc: String? = null,
            )
        }
    }
}

/** UP 主全部投稿（web medialist 接口）。id 即 aid，播放走 aid→bvid 兜底解析。 */
data class BiliSpaceMedialist(
    val code: Int = 0,
    val message: String? = null,
    val data: Data? = null,
) {
    data class Data(
        @SerializedName("total_count") val totalCount: Int = 0,
        @SerializedName("has_more") val hasMore: Boolean = false,
        @SerializedName("media_list") val mediaList: List<Media>? = null,
    ) {
        data class Media(
            @SerializedName("id") val id: Long = 0,
            @SerializedName("bv_id") val bvId: String? = null,
            @SerializedName("type") val type: Int = 0,
            @SerializedName("title") val title: String? = null,
            @SerializedName("cover") val cover: String? = null,
            /** 秒 */
            @SerializedName("duration") val duration: Int = 0,
            @SerializedName("pubtime") val pubtime: Long = 0,
            @SerializedName("upper") val upper: BiliArchive.Upper? = null,
            @SerializedName("cnt_info") val cntInfo: BiliArchive.CntInfo? = null,
            /** 1 = 已失效。 */
            @SerializedName("attr") val attr: Int = 0,
        )
    }
}

/** UP 主投稿列表（x/space/wbi/arc/search）。vlist 无 cid，播放时按 aid 解析。 */
data class BiliSpaceArcList(
    val code: Int = 0,
    val message: String? = null,
    val data: Data? = null,
) {
    data class Data(
        @SerializedName("list") val list: VideoList? = null,
        @SerializedName("page") val page: Page? = null,
    ) {
        data class VideoList(
            @SerializedName("vlist") val vlist: List<Archive>? = null,
        )

        data class Page(
            @SerializedName("pn") val pn: Int = 1,
            @SerializedName("ps") val ps: Int = 30,
            @SerializedName("count") val count: Int = 0,
        )

        data class Archive(
            @SerializedName("aid") val aid: Long = 0,
            @SerializedName("bvid") val bvid: String? = null,
            @SerializedName("title") val title: String? = null,
            @SerializedName("author") val author: String? = null,
            @SerializedName("mid") val mid: Long = 0,
            @SerializedName("pic") val pic: String? = null,
            /** 秒 */
            @SerializedName("length") val length: String? = null,
            @SerializedName("play") val play: Long = 0,
            @SerializedName("video_review") val videoReview: Long = 0,
            @SerializedName("created") val created: Long = 0,
        )
    }
}

/** UP 主空间分组（seasons=合集 / series=系列）。 */
data class BiliSpaceSeasonsSeries(    val code: Int = 0,
    val message: String? = null,
    val data: Data? = null,
) {
    data class Data(
        @SerializedName("items_lists") val itemsLists: ItemsLists? = null,
    ) {
        data class ItemsLists(
            @SerializedName("page") val page: Page? = null,
            @SerializedName("seasons_list") val seasonsList: List<Entry>? = null,
            @SerializedName("series_list") val seriesList: List<Entry>? = null,
        ) {
            data class Page(
                @SerializedName("page_num") val pageNum: Int = 1,
                @SerializedName("page_size") val pageSize: Int = 20,
                @SerializedName("total") val total: Int = 0,
            )

            /** meta 里 id 即 season_id（合集）/ series_id（系列），归档接口同用 season_id 参数。 */
            data class Entry(
                @SerializedName("meta") val meta: Meta? = null,
            ) {
                data class Meta(
                    @SerializedName("id") val id: Long = 0,
                    @SerializedName("name") val name: String? = null,
                    @SerializedName("description") val description: String? = null,
                    @SerializedName("total") val total: Int = 0,
                    @SerializedName("cover") val cover: String? = null,
                )
            }
        }
    }
}

/** 分组内投稿（正常页码分页）。 */
data class BiliSeasonArchives(
    val code: Int = 0,
    val message: String? = null,
    val data: Data? = null,
) {
    data class Data(
        @SerializedName("archives") val archives: List<BiliArchive>? = null,
        @SerializedName("page") val page: Page? = null,
    ) {
        data class Page(
            @SerializedName("page_num") val pageNum: Int = 1,
            @SerializedName("page_size") val pageSize: Int = 20,
            @SerializedName("total") val total: Int = 0,
        )
    }
}
