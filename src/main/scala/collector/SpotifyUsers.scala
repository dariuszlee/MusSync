import services.SpotifyUtility

object SpotifyCrawler extends App {
    val token = "BQDgtI25-oL2PApN4w-3UA9XsvZPG56dKKmuzfWjLRTnv3Zi1hkBHE9GG-nEoAGbd2MDYvzBlODiekGj2xohoc7OMG_MqxSU5fAmWJyDjI_MjH1gGNqBr_wVQ4oMLTkul0BPDRVsRjpar_AHucXz1I1tcKeHCmI"
    val user = "fishehh"

    var playlists = SpotifyUtility.get_playlists(token, user)
}
