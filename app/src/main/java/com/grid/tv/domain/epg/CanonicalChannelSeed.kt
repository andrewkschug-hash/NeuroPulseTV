package com.grid.tv.domain.epg

/**
 * Bundled canonical channel definitions for major US, CA, and UK networks.
 * EPG IDs are common XMLTV identifiers; final resolution still validates against live EPG sources.
 */
object CanonicalChannelSeed {

    data class Def(
        val id: String,
        val canonicalName: String,
        val country: String,
        val epgId: String,
        val category: String,
        val aliases: List<String>,
        val logoUrl: String? = null
    )

    val channels: List<Def> = listOf(
        Def("espn_us", "ESPN", "US", "espn.us", "Sports", listOf("ESPN HD", "ESPN East", "ESPN US", "ESPN-HD", "US ESPN")),
        Def("espn2_us", "ESPN 2", "US", "espn2.us", "Sports", listOf("ESPN2 HD", "ESPN 2 US", "ESPN2")),
        Def("espnu_us", "ESPNU", "US", "espnu.us", "Sports", listOf("ESPNU HD", "ESPN U")),
        Def("espnews_us", "ESPNews", "US", "espnews.us", "News", listOf("ESPNews HD", "ESPN News")),
        Def("fox_sports1_us", "Fox Sports 1", "US", "fs1.us", "Sports", listOf("FS1", "FS1 HD", "Fox Sports 1 HD")),
        Def("fox_sports2_us", "Fox Sports 2", "US", "fs2.us", "Sports", listOf("FS2", "FS2 HD")),
        Def("nfl_network_us", "NFL Network", "US", "nflnetwork.us", "Sports", listOf("NFL Network HD", "NFLN")),
        Def("nba_tv_us", "NBA TV", "US", "nbatv.us", "Sports", listOf("NBA TV HD", "NBA TV US")),
        Def("mlb_network_us", "MLB Network", "US", "mlbnetwork.us", "Sports", listOf("MLB Network HD", "MLB Network US")),
        Def("golf_channel_us", "Golf Channel", "US", "golfchannel.us", "Sports", listOf("Golf Channel HD")),
        Def("tsn_ca", "TSN", "CA", "tsn.ca", "Sports", listOf("TSN HD", "TSN 1", "TSN1", "TSN CA")),
        Def("tsn2_ca", "TSN 2", "CA", "tsn2.ca", "Sports", listOf("TSN2 HD", "TSN 2 CA")),
        Def("tsn3_ca", "TSN 3", "CA", "tsn3.ca", "Sports", listOf("TSN3 HD", "TSN 3 CA")),
        Def("tsn4_ca", "TSN 4", "CA", "tsn4.ca", "Sports", listOf("TSN4 HD", "TSN 4 CA")),
        Def("tsn5_ca", "TSN 5", "CA", "tsn5.ca", "Sports", listOf("TSN 5 CA", "TSN5 HD", "TSN 5")),
        Def("sportsnet_ca", "Sportsnet", "CA", "sportsnet.ca", "Sports", listOf("Sportsnet HD", "SN Canada", "Sportsnet CA")),
        Def("sportsnet_one_ca", "Sportsnet One", "CA", "sportsnetone.ca", "Sports", listOf("Sportsnet One HD", "SN One")),
        Def("cbc_ca", "CBC", "CA", "cbc.ca", "Entertainment", listOf("CBC HD", "CBC Television", "CBC CA")),
        Def("ctv_ca", "CTV", "CA", "ctv.ca", "Entertainment", listOf("CTV HD", "CTV CA")),
        Def("global_ca", "Global", "CA", "global.ca", "Entertainment", listOf("Global HD", "Global TV CA")),
        Def("cnn_us", "CNN", "US", "cnn.us", "News", listOf("CNN HD", "CNN US", "CNN International US")),
        Def("fox_news_us", "Fox News", "US", "foxnews.us", "News", listOf("Fox News HD", "Fox News Channel", "FNC")),
        Def("msnbc_us", "MSNBC", "US", "msnbc.us", "News", listOf("MSNBC HD")),
        Def("bbc_one_uk", "BBC One", "UK", "bbc1.uk", "Entertainment", listOf("BBC One HD", "BBC 1", "BBC1 HD")),
        Def("bbc_two_uk", "BBC Two", "UK", "bbc2.uk", "Entertainment", listOf("BBC Two HD", "BBC 2", "BBC2 HD")),
        Def("itv_uk", "ITV", "UK", "itv.uk", "Entertainment", listOf("ITV HD", "ITV 1", "ITV1 HD")),
        Def("channel4_uk", "Channel 4", "UK", "channel4.uk", "Entertainment", listOf("Channel 4 HD", "CH4 HD")),
        Def("channel5_uk", "Channel 5", "UK", "channel5.uk", "Entertainment", listOf("Channel 5 HD", "Five HD")),
        Def("sky_sports_main_uk", "Sky Sports Main Event", "UK", "skysportsmainevent.uk", "Sports", listOf("Sky Sports Main Event HD", "Sky Sports Main")),
        Def("sky_sports_football_uk", "Sky Sports Football", "UK", "skysportsfootball.uk", "Sports", listOf("Sky Sports Football HD")),
        Def("sky_sports_cricket_uk", "Sky Sports Cricket", "UK", "skysportscricket.uk", "Sports", listOf("Sky Sports Cricket HD")),
        Def("hbo_us", "HBO", "US", "hbo.us", "Movies", listOf("HBO HD", "HBO East", "HBO US")),
        Def("hbo_max_us", "HBO Max", "US", "hbomax.us", "Movies", listOf("Max", "HBO Max HD")),
        Def("showtime_us", "Showtime", "US", "showtime.us", "Movies", listOf("Showtime HD", "SHO HD")),
        Def("amc_us", "AMC", "US", "amc.us", "Movies", listOf("AMC HD")),
        Def("fx_us", "FX", "US", "fx.us", "Entertainment", listOf("FX HD", "FXX HD")),
        Def("discovery_us", "Discovery", "US", "discovery.us", "Entertainment", listOf("Discovery Channel", "Discovery Channel HD", "Discovery HD")),
        Def("natgeo_us", "National Geographic", "US", "natgeo.us", "Entertainment", listOf("Nat Geo HD", "National Geographic HD", "NGC")),
        Def("nickelodeon_us", "Nickelodeon", "US", "nickelodeon.us", "Kids", listOf("Nick HD", "Nickelodeon HD", "Nick US")),
        Def("cartoon_network_us", "Cartoon Network", "US", "cartoonnetwork.us", "Kids", listOf("Cartoon Network HD", "CN HD")),
        Def("disney_channel_us", "Disney Channel", "US", "disneychannel.us", "Kids", listOf("Disney Channel HD", "Disney HD")),
        Def("abc_us", "ABC", "US", "abc.us", "Entertainment", listOf("ABC HD", "ABC US", "ABC East")),
        Def("nbc_us", "NBC", "US", "nbc.us", "Entertainment", listOf("NBC HD", "NBC US", "NBC East")),
        Def("cbs_us", "CBS", "US", "cbs.us", "Entertainment", listOf("CBS HD", "CBS US", "CBS East")),
        Def("fox_us", "FOX", "US", "fox.us", "Entertainment", listOf("FOX HD", "Fox US", "Fox East")),
        Def("pbs_us", "PBS", "US", "pbs.us", "Entertainment", listOf("PBS HD", "PBS US")),
        Def("telemundo_us", "Telemundo", "US", "telemundo.us", "Entertainment", listOf("Telemundo HD")),
        Def("univision_us", "Univision", "US", "univision.us", "Entertainment", listOf("Univision HD")),
        Def("eurosport_uk", "Eurosport", "UK", "eurosport.uk", "Sports", listOf("Eurosport HD", "Eurosport 1", "Eurosport 1 HD")),
        Def("bt_sport_uk", "TNT Sports", "UK", "tntsports.uk", "Sports", listOf("BT Sport", "BT Sport HD", "TNT Sports HD"))
    )
}
