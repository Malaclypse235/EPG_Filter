package com.example.epgutility

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.widget.CompoundButton

class FiltersActivity : AppCompatActivity() {

    private lateinit var removeNonLatinCheckBox: CheckBox
    private lateinit var removeNonEnglishCheckBox: CheckBox

    private lateinit var checkboxRemoveSpanish: CheckBox
    private lateinit var buttonConfirm: Button
    private lateinit var buttonCancel: Button
    private lateinit var checkboxRemoveNewsWeather: CheckBox

    private lateinit var checkboxFoxNews: CheckBox
    private lateinit var checkboxCNN: CheckBox
    private lateinit var checkboxMSNBC: CheckBox
    private lateinit var checkboxNewsMax: CheckBox
    private lateinit var checkboxCNBC: CheckBox
    private lateinit var checkboxOAN: CheckBox
    private lateinit var checkboxWeatherChannel: CheckBox
    private lateinit var checkboxAccuWeather: CheckBox
    private lateinit var checkboxRemoveDuplicates: CheckBox
    private lateinit var checkboxNBCNews: CheckBox
    private lateinit var checkboxCBSNews: CheckBox

    // Sports Networks
    private lateinit var checkboxRemoveSportsChannels: CheckBox
    private lateinit var checkboxESPN: CheckBox
    private lateinit var checkboxFoxSports: CheckBox
    private lateinit var checkboxCBSSports: CheckBox
    private lateinit var checkboxNBCSports: CheckBox
    private lateinit var checkboxSportsnet: CheckBox
    private lateinit var checkboxTNTSports: CheckBox
    private lateinit var checkboxDAZN: CheckBox
    private lateinit var checkboxMLBNetwork: CheckBox
    private lateinit var checkboxNFLNetwork: CheckBox
    private lateinit var checkboxNBATV: CheckBox
    private lateinit var checkboxGolfChannel: CheckBox
    private lateinit var checkboxTennisChannel: CheckBox
    private lateinit var checkboxSKYSports: CheckBox
    private lateinit var checkboxNHLChannel: CheckBox
    private lateinit var checkboxBigTenNetwork: CheckBox

    // Sports by Type
    private lateinit var checkboxBaseball: CheckBox
    private lateinit var checkboxBasketball: CheckBox
    private lateinit var checkboxFootball: CheckBox
    private lateinit var checkboxSoccer: CheckBox
    private lateinit var checkboxHockey: CheckBox
    private lateinit var checkboxGolf: CheckBox
    private lateinit var checkboxFishing: CheckBox
    private lateinit var checkboxUFCMMA: CheckBox
    private lateinit var checkboxBoxing: CheckBox
    private lateinit var checkboxSwimming: CheckBox
    private lateinit var checkboxFifa: CheckBox
    private lateinit var checkboxF1: CheckBox
    private lateinit var checkboxXfc: CheckBox
    private lateinit var checkboxLucha: CheckBox
    private lateinit var checkboxSport: CheckBox

    // Music Networks
    private lateinit var checkboxRemoveAllMusic: CheckBox
    private lateinit var checkboxBETJams: CheckBox
    private lateinit var checkboxBETSoul: CheckBox
    private lateinit var checkboxBPM: CheckBox
    private lateinit var checkboxCMT: CheckBox
    private lateinit var checkboxClublandTV: CheckBox
    private lateinit var checkboxDaVinciMusic: CheckBox
    private lateinit var checkboxDanceMusicTV: CheckBox
    private lateinit var checkboxFlaunt: CheckBox
    private lateinit var checkboxFuse: CheckBox
    private lateinit var checkboxGospelMusicChannel: CheckBox
    private lateinit var checkboxHeartTV: CheckBox
    private lateinit var checkboxJuice: CheckBox
    private lateinit var checkboxJukebox: CheckBox
    private lateinit var checkboxKerrangTV: CheckBox
    private lateinit var checkboxKissTV: CheckBox
    private lateinit var checkboxLiteTV: CheckBox
    private lateinit var checkboxLoudTV: CheckBox
    private lateinit var checkboxMTV: CheckBox
    private lateinit var checkboxPulse: CheckBox
    private lateinit var checkboxQVCMusic: CheckBox
    private lateinit var checkboxRevolt: CheckBox
    private lateinit var checkboxRIDEtv: CheckBox
    private lateinit var checkboxStingray: CheckBox
    private lateinit var checkboxTheBox: CheckBox
    private lateinit var checkboxTrace: CheckBox
    private lateinit var checkboxVevo: CheckBox

    // Music by Genre
    private lateinit var checkboxAcappella: CheckBox
    private lateinit var checkboxAcoustic: CheckBox
    private lateinit var checkboxAlternative: CheckBox
    private lateinit var checkboxAmbient: CheckBox
    private lateinit var checkboxBollywood: CheckBox
    private lateinit var checkboxChildrensMusic: CheckBox
    private lateinit var checkboxChristianMusic: CheckBox
    private lateinit var checkboxClassical: CheckBox
    private lateinit var checkboxClassicRock: CheckBox
    private lateinit var checkboxCountry: CheckBox
    private lateinit var checkboxDance: CheckBox
    private lateinit var checkboxDisco: CheckBox
    private lateinit var checkboxEasyListening: CheckBox
    private lateinit var checkboxElectronic: CheckBox
    private lateinit var checkboxFolk: CheckBox
    private lateinit var checkboxGospel: CheckBox
    private lateinit var checkboxGrunge: CheckBox
    private lateinit var checkboxHardRock: CheckBox
    private lateinit var checkboxHipHop: CheckBox
    private lateinit var checkboxHolidayMusic: CheckBox
    private lateinit var checkboxIndie: CheckBox
    private lateinit var checkboxJazz: CheckBox
    private lateinit var checkboxKaraoke: CheckBox
    private lateinit var checkboxLatin: CheckBox
    private lateinit var checkboxLatinPop: CheckBox
    private lateinit var checkboxLofi: CheckBox
    private lateinit var checkboxMetal: CheckBox
    private lateinit var checkboxNewAge: CheckBox
    private lateinit var checkboxOpera: CheckBox
    private lateinit var checkboxPop: CheckBox
    private lateinit var checkboxPunk: CheckBox
    private lateinit var checkboxRnB: CheckBox
    private lateinit var checkboxRap: CheckBox
    private lateinit var checkboxReggae: CheckBox
    private lateinit var checkboxRock: CheckBox
    private lateinit var checkboxTechno: CheckBox
    private lateinit var checkboxTrance: CheckBox
    private lateinit var checkboxTriphop: CheckBox
    private lateinit var checkboxWorldMusic: CheckBox

    // Young Children - Master
    private lateinit var checkboxRemoveAllYoungChildren: CheckBox

    // Young Children - Channels
    private lateinit var checkboxNickJr: CheckBox
    private lateinit var checkboxDisneyJunior: CheckBox
    private lateinit var checkboxPBSKids: CheckBox
    private lateinit var checkboxUniversalKids: CheckBox
    private lateinit var checkboxBabyTV: CheckBox
    private lateinit var checkboxBoomerang: CheckBox
    private lateinit var checkboxCartoonito: CheckBox

    // Young Children - Keywords
    private lateinit var checkboxPawPatrol: CheckBox
    private lateinit var checkboxDoraTV: CheckBox
    private lateinit var checkboxMaxAndRuby: CheckBox
    private lateinit var checkboxArthur: CheckBox
    private lateinit var checkboxTheWiggles: CheckBox
    private lateinit var checkboxSpongeBob: CheckBox
    private lateinit var checkboxRetroKid: CheckBox
    private lateinit var checkboxRetroToons: CheckBox
    private lateinit var checkboxTeletubbies: CheckBox
    private lateinit var checkboxBobTheBuilder: CheckBox
    private lateinit var checkboxInspectorGadget: CheckBox
    private lateinit var checkboxBarneyAndFriends: CheckBox
    private lateinit var checkboxBarbieAndFriends: CheckBox
    private lateinit var checkboxHotwheels: CheckBox
    private lateinit var checkboxMoonbug: CheckBox
    private lateinit var checkboxBlippi: CheckBox
    private lateinit var checkboxCaillou: CheckBox
    private lateinit var checkboxFiremanSam: CheckBox
    private lateinit var checkboxBabyEinstein: CheckBox
    private lateinit var checkboxStrawberryShortcake: CheckBox
    private lateinit var checkboxHappyKids: CheckBox
    private lateinit var checkboxShaunTheSheep: CheckBox
    private lateinit var checkboxRainbowRuby: CheckBox
    private lateinit var checkboxZoomoo: CheckBox
    private lateinit var checkboxRevAndRoll: CheckBox
    private lateinit var checkboxTgJunior: CheckBox
    private lateinit var checkboxDuckTV: CheckBox
    private lateinit var checkboxSensicalJr: CheckBox
    private lateinit var checkboxKartoonChannel: CheckBox
    private lateinit var checkboxRyanAndFriends: CheckBox
    private lateinit var checkboxNinjaKids: CheckBox
    private lateinit var checkboxToonGoggles: CheckBox
    private lateinit var checkboxKidoodle: CheckBox
    private lateinit var checkboxPeppaPig: CheckBox
    private lateinit var checkboxBBCKids: CheckBox
    private lateinit var checkboxLittleStarsUniverse: CheckBox
    private lateinit var checkboxLittleAngelsPlayground: CheckBox
    private lateinit var checkboxBabyShark: CheckBox
    private lateinit var checkboxKidsAnime: CheckBox
    private lateinit var checkboxGoGoGadget: CheckBox
    private lateinit var checkboxForeverKids: CheckBox
    private lateinit var checkboxLoolooKids: CheckBox
    private lateinit var checkboxPocoyo: CheckBox
    private lateinit var checkboxKetchupTV: CheckBox
    private lateinit var checkboxSupertoonsTV: CheckBox
    private lateinit var checkboxYaaas: CheckBox
    private lateinit var checkboxSmurfTV: CheckBox
    private lateinit var checkboxBratTV: CheckBox
    private lateinit var checkboxCampSnoopy: CheckBox
    private lateinit var checkboxKidzBop: CheckBox

    // Reality Shows - Master
    private lateinit var checkboxRemoveAllRealityShows: CheckBox

    // Reality Shows - Channels
    private lateinit var checkboxAE: CheckBox
    private lateinit var checkboxBravo: CheckBox
    private lateinit var checkboxE: CheckBox
    private lateinit var checkboxLifetime: CheckBox
    private lateinit var checkboxMTVReality: CheckBox
    private lateinit var checkboxOWN: CheckBox
    private lateinit var checkboxTLC: CheckBox
    private lateinit var checkboxVH1: CheckBox
    private lateinit var checkboxWEtv: CheckBox

    // Reality Shows - Keywords
    private lateinit var checkbox90DayFiancee: CheckBox
    private lateinit var checkboxAmericanIdol: CheckBox
    private lateinit var checkboxAmazingRace: CheckBox
    private lateinit var checkboxBigBrother: CheckBox
    private lateinit var checkboxChallenge: CheckBox
    private lateinit var checkboxDancingWithTheStars: CheckBox
    private lateinit var checkboxDragRace: CheckBox
    private lateinit var checkboxDuckDynasty: CheckBox
    private lateinit var checkboxFlipOrFlop: CheckBox
    private lateinit var checkboxGordonRamsay: CheckBox
    private lateinit var checkboxHellSKitchen: CheckBox
    private lateinit var checkboxJerseyShore: CheckBox
    private lateinit var checkboxJudge: CheckBox
    private lateinit var checkboxKardashians: CheckBox
    private lateinit var checkboxLoveIsBlind: CheckBox
    private lateinit var checkboxLoveItOrListIt: CheckBox
    private lateinit var checkboxLoveIsland: CheckBox
    private lateinit var checkboxMasterchef: CheckBox
    private lateinit var checkboxMillionDollarListing: CheckBox
    private lateinit var checkboxNosey: CheckBox
    private lateinit var checkboxPerfectMatch: CheckBox
    private lateinit var checkboxPawnStars: CheckBox
    private lateinit var checkboxPropertyBrothers: CheckBox
    private lateinit var checkboxQueerEye: CheckBox
    private lateinit var checkboxRealHousewives: CheckBox
    private lateinit var checkboxSharkTank: CheckBox
    private lateinit var checkboxSinglesInferno: CheckBox
    private lateinit var checkboxStorageWars: CheckBox
    private lateinit var checkboxSurvivor: CheckBox
    private lateinit var checkboxTheBachelor: CheckBox
    private lateinit var checkboxTheBachelorette: CheckBox
    private lateinit var checkboxTheMaskedSinger: CheckBox
    private lateinit var checkboxTheOsbournes: CheckBox
    private lateinit var checkboxTheUltimatum: CheckBox
    private lateinit var checkboxTheVoice: CheckBox
    private lateinit var checkboxTopModel: CheckBox
    private lateinit var checkboxTeenMom: CheckBox
    private lateinit var checkboxTooHotToHandle: CheckBox
    private lateinit var checkboxVanderpumpRules: CheckBox

    // Current config data
    private lateinit var config: ConfigManager.ConfigData

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_filters)

        // Link to UI elements
        checkboxRemoveDuplicates = findViewById(R.id.checkboxRemoveDuplicates)
        removeNonLatinCheckBox = findViewById(R.id.checkboxNonLatin)
        removeNonEnglishCheckBox = findViewById(R.id.checkboxNonEnglish)
        checkboxRemoveSpanish = findViewById(R.id.checkboxRemoveSpanish)
        buttonConfirm = findViewById(R.id.buttonConfirm)
        buttonCancel = findViewById(R.id.buttonCancel)
        checkboxRemoveNewsWeather = findViewById(R.id.checkboxRemoveNewsWeather)

        checkboxFoxNews = findViewById(R.id.checkboxFoxNews)
        checkboxCNN = findViewById(R.id.checkboxCNN)
        checkboxMSNBC = findViewById(R.id.checkboxMSNBC)
        checkboxNewsMax = findViewById(R.id.checkboxNewsMax)
        checkboxCNBC = findViewById(R.id.checkboxCNBC)
        checkboxOAN = findViewById(R.id.checkboxOAN)
        checkboxWeatherChannel = findViewById(R.id.checkboxWeatherChannel)
        checkboxAccuWeather = findViewById(R.id.checkboxAccuWeather)
        checkboxNBCNews = findViewById(R.id.checkboxNBCNews)
        checkboxCBSNews = findViewById(R.id.checkboxCBSNews)

        // Sports Networks
        checkboxRemoveSportsChannels = findViewById(R.id.checkboxRemoveAllSports)
        checkboxESPN = findViewById(R.id.checkboxESPN)
        checkboxFoxSports = findViewById(R.id.checkboxFoxSports)
        checkboxCBSSports = findViewById(R.id.checkboxCBSSports)
        checkboxNBCSports = findViewById(R.id.checkboxNBCSports)
        checkboxSportsnet = findViewById(R.id.checkboxSportsnet)
        checkboxTNTSports = findViewById(R.id.checkboxTNTSports)
        checkboxDAZN = findViewById(R.id.checkboxDAZN)
        checkboxMLBNetwork = findViewById(R.id.checkboxMLBNetwork)
        checkboxNFLNetwork = findViewById(R.id.checkboxNFLNetwork)
        checkboxNBATV = findViewById(R.id.checkboxNBATV)
        checkboxGolfChannel = findViewById(R.id.checkboxGolfChannel)
        checkboxTennisChannel = findViewById(R.id.checkboxTennisChannel)
        checkboxSKYSports = findViewById(R.id.checkboxSKYSports)
        checkboxNHLChannel = findViewById(R.id.checkboxNHLChannel)
        checkboxBigTenNetwork = findViewById(R.id.checkboxBigTenNetwork)


// Sports by Type
        checkboxBaseball = findViewById(R.id.checkboxBaseball)
        checkboxBasketball = findViewById(R.id.checkboxBasketball)
        checkboxFootball = findViewById(R.id.checkboxFootball)
        checkboxSoccer = findViewById(R.id.checkboxSoccer)
        checkboxHockey = findViewById(R.id.checkboxHockey)
        checkboxGolf = findViewById(R.id.checkboxGolf)
        checkboxFishing = findViewById(R.id.checkboxFishing)
        checkboxUFCMMA = findViewById(R.id.checkboxUFCMMA)
        checkboxBoxing = findViewById(R.id.checkboxBoxing)
        checkboxSwimming = findViewById(R.id.checkboxSwimming)
        checkboxFifa = findViewById(R.id.checkboxFifa)
        checkboxF1 = findViewById(R.id.checkboxF1)
        checkboxXfc = findViewById(R.id.checkboxXfc)
        checkboxLucha = findViewById(R.id.checkboxLucha)
        checkboxSport = findViewById(R.id.checkboxSport)

        // Music Networks
        checkboxRemoveAllMusic = findViewById(R.id.checkboxRemoveAllMusic)
        checkboxBETJams = findViewById(R.id.checkboxBETJams)
        checkboxBETSoul = findViewById(R.id.checkboxBETSoul)
        checkboxBPM = findViewById(R.id.checkboxBPM)
        checkboxCMT = findViewById(R.id.checkboxCMT)
        checkboxClublandTV = findViewById(R.id.checkboxClublandTV)
        checkboxDaVinciMusic = findViewById(R.id.checkboxDaVinciMusic)
        checkboxDanceMusicTV = findViewById(R.id.checkboxDanceMusicTV)
        checkboxFlaunt = findViewById(R.id.checkboxFlaunt)
        checkboxFuse = findViewById(R.id.checkboxFuse)
        checkboxGospelMusicChannel = findViewById(R.id.checkboxGospelMusicChannel)
        checkboxHeartTV = findViewById(R.id.checkboxHeartTV)
        checkboxJuice = findViewById(R.id.checkboxJuice)
        checkboxJukebox = findViewById(R.id.checkboxJukebox)
        checkboxKerrangTV = findViewById(R.id.checkboxKerrangTV)
        checkboxKissTV = findViewById(R.id.checkboxKissTV)
        checkboxLiteTV = findViewById(R.id.checkboxLiteTV)
        checkboxLoudTV = findViewById(R.id.checkboxLoudTV)
        checkboxMTV = findViewById(R.id.checkboxMTV)
        checkboxPulse = findViewById(R.id.checkboxPulse)
        checkboxQVCMusic = findViewById(R.id.checkboxQVCMusic)
        checkboxRevolt = findViewById(R.id.checkboxRevolt)
        checkboxRIDEtv = findViewById(R.id.checkboxRIDEtv)
        checkboxStingray = findViewById(R.id.checkboxStingray)
        checkboxTheBox = findViewById(R.id.checkboxTheBox)
        checkboxTrace = findViewById(R.id.checkboxTrace)
        checkboxVevo = findViewById(R.id.checkboxVevo)

// Music by Genre
        checkboxAcappella = findViewById(R.id.checkboxAcappella)
        checkboxAcoustic = findViewById(R.id.checkboxAcoustic)
        checkboxAlternative = findViewById(R.id.checkboxAlternative)
        checkboxAmbient = findViewById(R.id.checkboxAmbient)
        checkboxBollywood = findViewById(R.id.checkboxBollywood)
        checkboxChildrensMusic = findViewById(R.id.checkboxChildrensMusic)
        checkboxChristianMusic = findViewById(R.id.checkboxChristianMusic)
        checkboxClassical = findViewById(R.id.checkboxClassical)
        checkboxClassicRock = findViewById(R.id.checkboxClassicRock)
        checkboxCountry = findViewById(R.id.checkboxCountry)
        checkboxDance = findViewById(R.id.checkboxDance)
        checkboxDisco = findViewById(R.id.checkboxDisco)
        checkboxEasyListening = findViewById(R.id.checkboxEasyListening)
        checkboxElectronic = findViewById(R.id.checkboxElectronic)
        checkboxFolk = findViewById(R.id.checkboxFolk)
        checkboxGospel = findViewById(R.id.checkboxGospel)
        checkboxGrunge = findViewById(R.id.checkboxGrunge)
        checkboxHardRock = findViewById(R.id.checkboxHardRock)
        checkboxHipHop = findViewById(R.id.checkboxHipHop)
        checkboxHolidayMusic = findViewById(R.id.checkboxHolidayMusic)
        checkboxIndie = findViewById(R.id.checkboxIndie)
        checkboxJazz = findViewById(R.id.checkboxJazz)
        checkboxKaraoke = findViewById(R.id.checkboxKaraoke)
        checkboxLatin = findViewById(R.id.checkboxLatin)
        checkboxLatinPop = findViewById(R.id.checkboxLatinPop)
        checkboxLofi = findViewById(R.id.checkboxLofi)
        checkboxMetal = findViewById(R.id.checkboxMetal)
        checkboxNewAge = findViewById(R.id.checkboxNewAge)
        checkboxOpera = findViewById(R.id.checkboxOpera)
        checkboxPop = findViewById(R.id.checkboxPop)
        checkboxPunk = findViewById(R.id.checkboxPunk)
        checkboxRnB = findViewById(R.id.checkboxRnB)
        checkboxRap = findViewById(R.id.checkboxRap)
        checkboxReggae = findViewById(R.id.checkboxReggae)
        checkboxRock = findViewById(R.id.checkboxRock)
        checkboxTechno = findViewById(R.id.checkboxTechno)
        checkboxTrance = findViewById(R.id.checkboxTrance)
        checkboxTriphop = findViewById(R.id.checkboxTriphop)
        checkboxWorldMusic = findViewById(R.id.checkboxWorldMusic)

        // Young Children - Master
        checkboxRemoveAllYoungChildren = findViewById(R.id.checkboxRemoveAllYoungChildren)

// Young Children - Channels
        checkboxNickJr = findViewById(R.id.checkboxNickJr)
        checkboxDisneyJunior = findViewById(R.id.checkboxDisneyJunior)
        checkboxPBSKids = findViewById(R.id.checkboxPBSKids)
        checkboxUniversalKids = findViewById(R.id.checkboxUniversalKids)
        checkboxBabyTV = findViewById(R.id.checkboxBabyTV)
        checkboxBoomerang = findViewById(R.id.checkboxBoomerang)
        checkboxCartoonito = findViewById(R.id.checkboxCartoonito)

// Young Children - Keywords
        checkboxPawPatrol = findViewById(R.id.checkboxPawPatrol)
        checkboxDoraTV = findViewById(R.id.checkboxDoraTV)
        checkboxMaxAndRuby = findViewById(R.id.checkboxMaxAndRuby)
        checkboxArthur = findViewById(R.id.checkboxArthur)
        checkboxTheWiggles = findViewById(R.id.checkboxTheWiggles)
        checkboxSpongeBob = findViewById(R.id.checkboxSpongeBob)
        checkboxRetroKid = findViewById(R.id.checkboxRetroKid)
        checkboxRetroToons = findViewById(R.id.checkboxRetroToons)
        checkboxTeletubbies = findViewById(R.id.checkboxTeletubbies)
        checkboxBobTheBuilder = findViewById(R.id.checkboxBobTheBuilder)
        checkboxInspectorGadget = findViewById(R.id.checkboxInspectorGadget)
        checkboxBarneyAndFriends = findViewById(R.id.checkboxBarneyAndFriends)
        checkboxBarbieAndFriends = findViewById(R.id.checkboxBarbieAndFriends)
        checkboxHotwheels = findViewById(R.id.checkboxHotwheels)
        checkboxMoonbug = findViewById(R.id.checkboxMoonbug)
        checkboxBlippi = findViewById(R.id.checkboxBlippi)
        checkboxCaillou = findViewById(R.id.checkboxCaillou)
        checkboxFiremanSam = findViewById(R.id.checkboxFiremanSam)
        checkboxBabyEinstein = findViewById(R.id.checkboxBabyEinstein)
        checkboxStrawberryShortcake = findViewById(R.id.checkboxStrawberryShortcake)
        checkboxHappyKids = findViewById(R.id.checkboxHappyKids)
        checkboxShaunTheSheep = findViewById(R.id.checkboxShaunTheSheep)
        checkboxRainbowRuby = findViewById(R.id.checkboxRainbowRuby)
        checkboxZoomoo = findViewById(R.id.checkboxZoomoo)
        checkboxRevAndRoll = findViewById(R.id.checkboxRevAndRoll)
        checkboxTgJunior = findViewById(R.id.checkboxTgJunior)
        checkboxDuckTV = findViewById(R.id.checkboxDuckTV)
        checkboxSensicalJr = findViewById(R.id.checkboxSensicalJr)
        checkboxKartoonChannel = findViewById(R.id.checkboxKartoonChannel)
        checkboxRyanAndFriends = findViewById(R.id.checkboxRyanAndFriends)
        checkboxNinjaKids = findViewById(R.id.checkboxNinjaKids)
        checkboxToonGoggles = findViewById(R.id.checkboxToonGoggles)
        checkboxKidoodle = findViewById(R.id.checkboxKidoodle)
        checkboxPeppaPig = findViewById(R.id.checkboxPeppaPig)
        checkboxBBCKids = findViewById(R.id.checkboxBBCKids)
        checkboxLittleStarsUniverse = findViewById(R.id.checkboxLittleStarsUniverse)
        checkboxLittleAngelsPlayground = findViewById(R.id.checkboxLittleAngelsPlayground)
        checkboxBabyShark = findViewById(R.id.checkboxBabyShark)
        checkboxKidsAnime = findViewById(R.id.checkboxKidsAnime)
        checkboxGoGoGadget = findViewById(R.id.checkboxGoGoGadget)
        checkboxForeverKids = findViewById(R.id.checkboxForeverKids)
        checkboxLoolooKids = findViewById(R.id.checkboxLoolooKids)
        checkboxPocoyo = findViewById(R.id.checkboxPocoyo)
        checkboxKetchupTV = findViewById(R.id.checkboxKetchupTV)
        checkboxSupertoonsTV = findViewById(R.id.checkboxSupertoonsTV)
        checkboxYaaas = findViewById(R.id.checkboxYaaas)
        checkboxSmurfTV = findViewById(R.id.checkboxSmurfTV)
        checkboxBratTV = findViewById(R.id.checkboxBratTV)
        checkboxCampSnoopy = findViewById(R.id.checkboxCampSnoopy)
        checkboxKidzBop = findViewById(R.id.checkboxKidzBop)

        // Reality Shows - Master
        checkboxRemoveAllRealityShows = findViewById(R.id.checkboxRemoveAllRealityShows)

// Reality Shows - Channels
        checkboxAE = findViewById(R.id.checkboxAE)
        checkboxBravo = findViewById(R.id.checkboxBravo)
        checkboxE = findViewById(R.id.checkboxE)
        checkboxLifetime = findViewById(R.id.checkboxLifetime)
        checkboxMTVReality = findViewById(R.id.checkboxMTVReality)
        checkboxOWN = findViewById(R.id.checkboxOWN)
        checkboxTLC = findViewById(R.id.checkboxTLC)
        checkboxVH1 = findViewById(R.id.checkboxVH1)
        checkboxWEtv = findViewById(R.id.checkboxWEtv)

// Reality Shows - Keywords
        checkbox90DayFiancee = findViewById(R.id.checkbox90DayFiancee)
        checkboxAmericanIdol = findViewById(R.id.checkboxAmericanIdol)
        checkboxAmazingRace = findViewById(R.id.checkboxAmazingRace)
        checkboxBigBrother = findViewById(R.id.checkboxBigBrother)
        checkboxChallenge = findViewById(R.id.checkboxChallenge)
        checkboxDancingWithTheStars = findViewById(R.id.checkboxDancingWithTheStars)
        checkboxDragRace = findViewById(R.id.checkboxDragRace)
        checkboxDuckDynasty = findViewById(R.id.checkboxDuckDynasty)
        checkboxFlipOrFlop = findViewById(R.id.checkboxFlipOrFlop)
        checkboxGordonRamsay = findViewById(R.id.checkboxGordonRamsay)
        checkboxHellSKitchen = findViewById(R.id.checkboxHellSKitchen)
        checkboxJerseyShore = findViewById(R.id.checkboxJerseyShore)
        checkboxJudge = findViewById(R.id.checkboxJudge)
        checkboxKardashians = findViewById(R.id.checkboxKardashians)
        checkboxLoveIsBlind = findViewById(R.id.checkboxLoveIsBlind)
        checkboxLoveItOrListIt = findViewById(R.id.checkboxLoveItOrListIt)
        checkboxLoveIsland = findViewById(R.id.checkboxLoveIsland)
        checkboxMasterchef = findViewById(R.id.checkboxMasterchef)
        checkboxMillionDollarListing = findViewById(R.id.checkboxMillionDollarListing)
        checkboxNosey = findViewById(R.id.checkboxNosey)
        checkboxPerfectMatch = findViewById(R.id.checkboxPerfectMatch)
        checkboxPawnStars = findViewById(R.id.checkboxPawnStars)
        checkboxPropertyBrothers = findViewById(R.id.checkboxPropertyBrothers)
        checkboxQueerEye = findViewById(R.id.checkboxQueerEye)
        checkboxRealHousewives = findViewById(R.id.checkboxRealHousewives)
        checkboxSharkTank = findViewById(R.id.checkboxSharkTank)
        checkboxSinglesInferno = findViewById(R.id.checkboxSinglesInferno)
        checkboxStorageWars = findViewById(R.id.checkboxStorageWars)
        checkboxSurvivor = findViewById(R.id.checkboxSurvivor)
        checkboxTheBachelor = findViewById(R.id.checkboxTheBachelor)
        checkboxTheBachelorette = findViewById(R.id.checkboxTheBachelorette)
        checkboxTheMaskedSinger = findViewById(R.id.checkboxTheMaskedSinger)
        checkboxTheOsbournes = findViewById(R.id.checkboxTheOsbournes)
        checkboxTheUltimatum = findViewById(R.id.checkboxTheUltimatum)
        checkboxTheVoice = findViewById(R.id.checkboxTheVoice)
        checkboxTopModel = findViewById(R.id.checkboxTopModel)
        checkboxTeenMom = findViewById(R.id.checkboxTeenMom)
        checkboxTooHotToHandle = findViewById(R.id.checkboxTooHotToHandle)
        checkboxVanderpumpRules = findViewById(R.id.checkboxVanderpumpRules)



        // Load config (create defaults if missing)
        val loadResult = ConfigManager.loadConfig(this)
        config = loadResult.config

        // If config files didn't exist, we now force-save defaults
        if (!configExists()) {
            ConfigManager.saveConfig(this, config)
        }

        /* Show toast if we lost file access (but still allow filters)
        if (loadResult.revokedPermissions) {
            Toast.makeText(
                this,
                "⚠️ File permissions were revoked. Please re-select your files on the main screen.",
                Toast.LENGTH_LONG
            ).show()
        }
        */

        // Initialize checkboxes with saved values
        // ✅ FIXED: now reads from config.filters
        removeNonLatinCheckBox.isChecked = config.filters.removeNonLatin
        removeNonEnglishCheckBox.isChecked = config.filters.removeNonEnglish
        checkboxRemoveSpanish.isChecked = config.filters.removeSpanish
        // Load News & Weather filter states
        checkboxRemoveNewsWeather.isChecked = config.filters.removeNewsAndWeather
        checkboxRemoveDuplicates.isChecked = config.filters.removeDuplicates

        checkboxFoxNews.isChecked = config.filters.removeFoxNews
        checkboxCNN.isChecked = config.filters.removeCNN
        checkboxMSNBC.isChecked = config.filters.removeMSNBC
        checkboxNewsMax.isChecked = config.filters.removeNewsMax
        checkboxCNBC.isChecked = config.filters.removeCNBC
        checkboxOAN.isChecked = config.filters.removeOAN
        checkboxWeatherChannel.isChecked = config.filters.removeWeatherChannel
        checkboxAccuWeather.isChecked = config.filters.removeAccuWeather
        checkboxNBCNews.isChecked = config.filters.removeNBCNews
        checkboxCBSNews.isChecked = config.filters.removeCBSNews

        // Restore Sports Networks
        checkboxRemoveSportsChannels.isChecked = config.filters.removeSportsChannels
        checkboxESPN.isChecked = config.filters.removeESPN
        checkboxFoxSports.isChecked = config.filters.removeFoxSports
        checkboxCBSSports.isChecked = config.filters.removeCBSSports
        checkboxNBCSports.isChecked = config.filters.removeNBCSports
        checkboxSportsnet.isChecked = config.filters.removeSportsnet
        checkboxTNTSports.isChecked = config.filters.removeTNTSports
        checkboxDAZN.isChecked = config.filters.removeDAZN
        checkboxMLBNetwork.isChecked = config.filters.removeMLBNetwork
        checkboxNFLNetwork.isChecked = config.filters.removeNFLNetwork
        checkboxNBATV.isChecked = config.filters.removeNBATV
        checkboxGolfChannel.isChecked = config.filters.removeGolfChannel
        checkboxTennisChannel.isChecked = config.filters.removeTennisChannel
        checkboxSKYSports.isChecked = config.filters.removeSKYSports
        checkboxNHLChannel.isChecked = config.filters.removeNHLChannel
        checkboxBigTenNetwork.isChecked = config.filters.removeBigTenNetwork

// Restore Sports by Type
        checkboxBaseball.isChecked = config.filters.removeBaseball
        checkboxBasketball.isChecked = config.filters.removeBasketball
        checkboxFootball.isChecked = config.filters.removeFootball
        checkboxSoccer.isChecked = config.filters.removeSoccer
        checkboxHockey.isChecked = config.filters.removeHockey
        checkboxGolf.isChecked = config.filters.removeGolf
        checkboxFishing.isChecked = config.filters.removeFishing
        checkboxUFCMMA.isChecked = config.filters.removeUFCMMA
        checkboxBoxing.isChecked = config.filters.removeBoxing
        checkboxSwimming.isChecked = config.filters.removeSwimming
        checkboxFifa.isChecked = config.filters.removeFifa
        checkboxF1.isChecked = config.filters.removeF1
        checkboxXfc.isChecked = config.filters.removeXfc
        checkboxLucha.isChecked = config.filters.removeLucha
        checkboxSport.isChecked = config.filters.removeSport

        // Restore Music Networks
        checkboxRemoveAllMusic.isChecked = config.filters.removeMusicChannels
        checkboxBETJams.isChecked = config.filters.removeBETJams
        checkboxBETSoul.isChecked = config.filters.removeBETSoul
        checkboxBPM.isChecked = config.filters.removeBPM
        checkboxCMT.isChecked = config.filters.removeCMT
        checkboxClublandTV.isChecked = config.filters.removeClublandTV
        checkboxDaVinciMusic.isChecked = config.filters.removeDaVinciMusic
        checkboxDanceMusicTV.isChecked = config.filters.removeDanceMusicTV
        checkboxFlaunt.isChecked = config.filters.removeFlaunt
        checkboxFuse.isChecked = config.filters.removeFuse
        checkboxGospelMusicChannel.isChecked = config.filters.removeGospelMusicChannel
        checkboxHeartTV.isChecked = config.filters.removeHeartTV
        checkboxJuice.isChecked = config.filters.removeJuice
        checkboxJukebox.isChecked = config.filters.removeJukebox
        checkboxKerrangTV.isChecked = config.filters.removeKerrangTV
        checkboxKissTV.isChecked = config.filters.removeKissTV
        checkboxLiteTV.isChecked = config.filters.removeLiteTV
        checkboxLoudTV.isChecked = config.filters.removeLoudTV
        checkboxMTV.isChecked = config.filters.removeMTV
        checkboxPulse.isChecked = config.filters.removePulse
        checkboxQVCMusic.isChecked = config.filters.removeQVCMusic
        checkboxRevolt.isChecked = config.filters.removeRevolt
        checkboxRIDEtv.isChecked = config.filters.removeRIDEtv
        checkboxStingray.isChecked = config.filters.removeStingray
        checkboxTheBox.isChecked = config.filters.removeTheBox
        checkboxTrace.isChecked = config.filters.removeTrace
        checkboxVevo.isChecked = config.filters.removeVevo

// Restore Music by Genre
        checkboxAcappella.isChecked = config.filters.removeAcappella
        checkboxAcoustic.isChecked = config.filters.removeAcoustic
        checkboxAlternative.isChecked = config.filters.removeAlternative
        checkboxAmbient.isChecked = config.filters.removeAmbient
        checkboxBollywood.isChecked = config.filters.removeBollywood
        checkboxChildrensMusic.isChecked = config.filters.removeChildrensMusic
        checkboxChristianMusic.isChecked = config.filters.removeChristianMusic
        checkboxClassical.isChecked = config.filters.removeClassical
        checkboxClassicRock.isChecked = config.filters.removeClassicRock
        checkboxCountry.isChecked = config.filters.removeCountry
        checkboxDance.isChecked = config.filters.removeDance
        checkboxDisco.isChecked = config.filters.removeDisco
        checkboxEasyListening.isChecked = config.filters.removeEasyListening
        checkboxElectronic.isChecked = config.filters.removeElectronic
        checkboxFolk.isChecked = config.filters.removeFolk
        checkboxGospel.isChecked = config.filters.removeGospel
        checkboxGrunge.isChecked = config.filters.removeGrunge
        checkboxHardRock.isChecked = config.filters.removeHardRock
        checkboxHipHop.isChecked = config.filters.removeHipHop
        checkboxHolidayMusic.isChecked = config.filters.removeHolidayMusic
        checkboxIndie.isChecked = config.filters.removeIndie
        checkboxJazz.isChecked = config.filters.removeJazz
        checkboxKaraoke.isChecked = config.filters.removeKaraoke
        checkboxLatin.isChecked = config.filters.removeLatin
        checkboxLatinPop.isChecked = config.filters.removeLatinPop
        checkboxLofi.isChecked = config.filters.removeLofi
        checkboxMetal.isChecked = config.filters.removeMetal
        checkboxNewAge.isChecked = config.filters.removeNewAge
        checkboxOpera.isChecked = config.filters.removeOpera
        checkboxPop.isChecked = config.filters.removePop
        checkboxPunk.isChecked = config.filters.removePunk
        checkboxRnB.isChecked = config.filters.removeRnB
        checkboxRap.isChecked = config.filters.removeRap
        checkboxReggae.isChecked = config.filters.removeReggae
        checkboxRock.isChecked = config.filters.removeRock
        checkboxTechno.isChecked = config.filters.removeTechno
        checkboxTrance.isChecked = config.filters.removeTrance
        checkboxTriphop.isChecked = config.filters.removeTriphop
        checkboxWorldMusic.isChecked = config.filters.removeWorldMusic

        // Load Young Children - Master
        checkboxRemoveAllYoungChildren.isChecked = config.filters.removeAllYoungChildren

// Load Young Children - Channels
        checkboxNickJr.isChecked = config.filters.removeNickJr
        checkboxDisneyJunior.isChecked = config.filters.removeDisneyJunior
        checkboxPBSKids.isChecked = config.filters.removePBSKids
        checkboxUniversalKids.isChecked = config.filters.removeUniversalKids
        checkboxBabyTV.isChecked = config.filters.removeBabyTV
        checkboxBoomerang.isChecked = config.filters.removeBoomerang
        checkboxCartoonito.isChecked = config.filters.removeCartoonito

// Load Young Children - Keywords
        checkboxPawPatrol.isChecked = config.filters.removePawPatrol
        checkboxDoraTV.isChecked = config.filters.removeDoraTV
        checkboxMaxAndRuby.isChecked = config.filters.removeMaxAndRuby
        checkboxArthur.isChecked = config.filters.removeArthur
        checkboxTheWiggles.isChecked = config.filters.removeTheWiggles
        checkboxSpongeBob.isChecked = config.filters.removeSpongeBob
        checkboxRetroKid.isChecked = config.filters.removeRetroKid
        checkboxRetroToons.isChecked = config.filters.removeRetroToons
        checkboxTeletubbies.isChecked = config.filters.removeTeletubbies
        checkboxBobTheBuilder.isChecked = config.filters.removeBobTheBuilder
        checkboxInspectorGadget.isChecked = config.filters.removeInspectorGadget
        checkboxBarneyAndFriends.isChecked = config.filters.removeBarneyAndFriends
        checkboxBarbieAndFriends.isChecked = config.filters.removeBarbieAndFriends
        checkboxHotwheels.isChecked = config.filters.removeHotwheels
        checkboxMoonbug.isChecked = config.filters.removeMoonbug
        checkboxBlippi.isChecked = config.filters.removeBlippi
        checkboxCaillou.isChecked = config.filters.removeCaillou
        checkboxFiremanSam.isChecked = config.filters.removeFiremanSam
        checkboxBabyEinstein.isChecked = config.filters.removeBabyEinstein
        checkboxStrawberryShortcake.isChecked = config.filters.removeStrawberryShortcake
        checkboxHappyKids.isChecked = config.filters.removeHappyKids
        checkboxShaunTheSheep.isChecked = config.filters.removeShaunTheSheep
        checkboxRainbowRuby.isChecked = config.filters.removeRainbowRuby
        checkboxZoomoo.isChecked = config.filters.removeZoomoo
        checkboxRevAndRoll.isChecked = config.filters.removeRevAndRoll
        checkboxTgJunior.isChecked = config.filters.removeTgJunior
        checkboxDuckTV.isChecked = config.filters.removeDuckTV
        checkboxSensicalJr.isChecked = config.filters.removeSensicalJr
        checkboxKartoonChannel.isChecked = config.filters.removeKartoonChannel
        checkboxRyanAndFriends.isChecked = config.filters.removeRyanAndFriends
        checkboxNinjaKids.isChecked = config.filters.removeNinjaKids
        checkboxToonGoggles.isChecked = config.filters.removeToonGoggles
        checkboxKidoodle.isChecked = config.filters.removeKidoodle
        checkboxPeppaPig.isChecked = config.filters.removePeppaPig
        checkboxBBCKids.isChecked = config.filters.removeBbcKids
        checkboxLittleStarsUniverse.isChecked = config.filters.removeLittleStarsUniverse
        checkboxLittleAngelsPlayground.isChecked = config.filters.removeLittleAngelsPlayground
        checkboxBabyShark.isChecked = config.filters.removeBabyShark
        checkboxKidsAnime.isChecked = config.filters.removeKidsAnime
        checkboxGoGoGadget.isChecked = config.filters.removeGoGoGadget
        checkboxForeverKids.isChecked = config.filters.removeForeverKids
        checkboxLoolooKids.isChecked = config.filters.removeLoolooKids
        checkboxPocoyo.isChecked = config.filters.removePocoyo
        checkboxKetchupTV.isChecked = config.filters.removeKetchupTV
        checkboxSupertoonsTV.isChecked = config.filters.removeSupertoonsTV
        checkboxYaaas.isChecked = config.filters.removeYaaas
        checkboxSmurfTV.isChecked = config.filters.removeSmurfTV
        checkboxBratTV.isChecked = config.filters.removeBratTV
        checkboxCampSnoopy.isChecked = config.filters.removeCampSnoopy
        checkboxKidzBop.isChecked = config.filters.removeKidzBop



        // Load Reality Shows - Master
        checkboxRemoveAllRealityShows.isChecked = config.filters.removeAllRealityShows

// Load Reality Shows - Channels
        checkboxAE.isChecked = config.filters.removeAE
        checkboxBravo.isChecked = config.filters.removeBravo
        checkboxE.isChecked = config.filters.removeE
        checkboxLifetime.isChecked = config.filters.removeLifetime
        checkboxOWN.isChecked = config.filters.removeOWN
        checkboxTLC.isChecked = config.filters.removeTLC
        checkboxVH1.isChecked = config.filters.removeVH1
        checkboxWEtv.isChecked = config.filters.removeWEtv

// Load Reality Shows - Keywords
        checkbox90DayFiancee.isChecked = config.filters.remove90DayFiancee
        checkboxAmericanIdol.isChecked = config.filters.removeAmericanIdol
        checkboxAmazingRace.isChecked = config.filters.removeAmazingRace
        checkboxBigBrother.isChecked = config.filters.removeBigBrother
        checkboxChallenge.isChecked = config.filters.removeChallenge
        checkboxDancingWithTheStars.isChecked = config.filters.removeDancingWithTheStars
        checkboxDragRace.isChecked = config.filters.removeDragRace
        checkboxDuckDynasty.isChecked = config.filters.removeDuckDynasty
        checkboxFlipOrFlop.isChecked = config.filters.removeFlipOrFlop
        checkboxGordonRamsay.isChecked = config.filters.removeGordonRamsay
        checkboxHellSKitchen.isChecked = config.filters.removeHellSKitchen
        checkboxJerseyShore.isChecked = config.filters.removeJerseyShore
        checkboxJudge.isChecked = config.filters.removeJudge
        checkboxKardashians.isChecked = config.filters.removeKardashians
        checkboxLoveIsBlind.isChecked = config.filters.removeLoveIsBlind
        checkboxLoveItOrListIt.isChecked = config.filters.removeLoveItOrListIt
        checkboxLoveIsland.isChecked = config.filters.removeLoveIsland
        checkboxMasterchef.isChecked = config.filters.removeMasterchef
        checkboxMillionDollarListing.isChecked = config.filters.removeMillionDollarListing
        checkboxNosey.isChecked = config.filters.removeNosey
        checkboxPerfectMatch.isChecked = config.filters.removePerfectMatch
        checkboxPawnStars.isChecked = config.filters.removePawnStars
        checkboxPropertyBrothers.isChecked = config.filters.removePropertyBrothers
        checkboxQueerEye.isChecked = config.filters.removeQueerEye
        checkboxRealHousewives.isChecked = config.filters.removeRealHousewives
        checkboxSharkTank.isChecked = config.filters.removeSharkTank
        checkboxSinglesInferno.isChecked = config.filters.removeSinglesInferno
        checkboxStorageWars.isChecked = config.filters.removeStorageWars
        checkboxSurvivor.isChecked = config.filters.removeSurvivor
        checkboxTheBachelor.isChecked = config.filters.removeTheBachelor
        checkboxTheBachelorette.isChecked = config.filters.removeTheBachelorette
        checkboxTheMaskedSinger.isChecked = config.filters.removeTheMaskedSinger
        checkboxTheOsbournes.isChecked = config.filters.removeTheOsbournes
        checkboxTheUltimatum.isChecked = config.filters.removeTheUltimatum
        checkboxTheVoice.isChecked = config.filters.removeTheVoice
        checkboxTopModel.isChecked = config.filters.removeTopModel
        checkboxTeenMom.isChecked = config.filters.removeTeenMom
        checkboxTooHotToHandle.isChecked = config.filters.removeTooHotToHandle
        checkboxVanderpumpRules.isChecked = config.filters.removeVanderpumpRules





        // Add checkbox interaction logic
        setupCheckboxInteractions()


        // 🔗 Sync MTV checkboxes (Music ↔ Reality) - Simple双向 sync
        checkboxMTV.setOnCheckedChangeListener { _, isChecked ->
            if (checkboxMTVReality.isPressed) return@setOnCheckedChangeListener
            checkboxMTVReality.isChecked = isChecked
        }

        checkboxMTVReality.setOnCheckedChangeListener { _, isChecked ->
            if (checkboxMTV.isPressed) return@setOnCheckedChangeListener
            checkboxMTV.isChecked = isChecked
        }

// Load state: use Music as source of truth
        val mtvChecked = config.filters.removeMTV
        checkboxMTV.isChecked = mtvChecked
        checkboxMTVReality.isChecked = mtvChecked  // Sync on load

// But also respect Reality setting if different (merge)
        if (config.filters.removeMTVReality) {
            checkboxMTV.isChecked = true
            checkboxMTVReality.isChecked = true
        }

        // Save changes when confirm button pressed
        buttonConfirm.setOnClickListener {
            // Update config from UI
            // ✅ FIXED: now writes to config.filters
            config.filters.removeNonLatin = removeNonLatinCheckBox.isChecked
            config.filters.removeNonEnglish = removeNonEnglishCheckBox.isChecked
            config.filters.removeSpanish = checkboxRemoveSpanish.isChecked
            // Save News & Weather filter states
            config.filters.removeNewsAndWeather = checkboxRemoveNewsWeather.isChecked

            config.filters.removeFoxNews = checkboxFoxNews.isChecked
            config.filters.removeCNN = checkboxCNN.isChecked
            config.filters.removeMSNBC = checkboxMSNBC.isChecked
            config.filters.removeNewsMax = checkboxNewsMax.isChecked
            config.filters.removeCNBC = checkboxCNBC.isChecked
            config.filters.removeOAN = checkboxOAN.isChecked
            config.filters.removeWeatherChannel = checkboxWeatherChannel.isChecked
            config.filters.removeAccuWeather = checkboxAccuWeather.isChecked
            config.filters.removeDuplicates = checkboxRemoveDuplicates.isChecked
            config.filters.removeNBCNews = checkboxNBCNews.isChecked
            config.filters.removeCBSNews = checkboxCBSNews.isChecked

            // Save Sports Networks
            config.filters.removeSportsChannels = checkboxRemoveSportsChannels.isChecked
            config.filters.removeESPN = checkboxESPN.isChecked
            config.filters.removeFoxSports = checkboxFoxSports.isChecked
            config.filters.removeCBSSports = checkboxCBSSports.isChecked
            config.filters.removeNBCSports = checkboxNBCSports.isChecked
            config.filters.removeSportsnet = checkboxSportsnet.isChecked
            config.filters.removeTNTSports = checkboxTNTSports.isChecked
            config.filters.removeDAZN = checkboxDAZN.isChecked
            config.filters.removeMLBNetwork = checkboxMLBNetwork.isChecked
            config.filters.removeNFLNetwork = checkboxNFLNetwork.isChecked
            config.filters.removeNBATV = checkboxNBATV.isChecked
            config.filters.removeGolfChannel = checkboxGolfChannel.isChecked
            config.filters.removeTennisChannel = checkboxTennisChannel.isChecked
            config.filters.removeSKYSports = checkboxSKYSports.isChecked
            config.filters.removeNHLChannel = checkboxNHLChannel.isChecked
            config.filters.removeBigTenNetwork = checkboxBigTenNetwork.isChecked

// Save Sports by Type
            config.filters.removeBaseball = checkboxBaseball.isChecked
            config.filters.removeBasketball = checkboxBasketball.isChecked
            config.filters.removeFootball = checkboxFootball.isChecked
            config.filters.removeSoccer = checkboxSoccer.isChecked
            config.filters.removeHockey = checkboxHockey.isChecked
            config.filters.removeGolf = checkboxGolf.isChecked
            config.filters.removeFishing = checkboxFishing.isChecked
            config.filters.removeUFCMMA = checkboxUFCMMA.isChecked
            config.filters.removeBoxing = checkboxBoxing.isChecked
            config.filters.removeSwimming = checkboxSwimming.isChecked
            config.filters.removeFifa = checkboxFifa.isChecked
            config.filters.removeF1 = checkboxF1.isChecked
            config.filters.removeXfc = checkboxXfc.isChecked
            config.filters.removeLucha = checkboxLucha.isChecked
            config.filters.removeSport = checkboxSport.isChecked


            // Save Music Networks
            config.filters.removeMusicChannels = checkboxRemoveAllMusic.isChecked
            config.filters.removeBETJams = checkboxBETJams.isChecked
            config.filters.removeBETSoul = checkboxBETSoul.isChecked
            config.filters.removeBPM = checkboxBPM.isChecked
            config.filters.removeCMT = checkboxCMT.isChecked
            config.filters.removeClublandTV = checkboxClublandTV.isChecked
            config.filters.removeDaVinciMusic = checkboxDaVinciMusic.isChecked
            config.filters.removeDanceMusicTV = checkboxDanceMusicTV.isChecked
            config.filters.removeFlaunt = checkboxFlaunt.isChecked
            config.filters.removeFuse = checkboxFuse.isChecked
            config.filters.removeGospelMusicChannel = checkboxGospelMusicChannel.isChecked
            config.filters.removeHeartTV = checkboxHeartTV.isChecked
            config.filters.removeJuice = checkboxJuice.isChecked
            config.filters.removeJukebox = checkboxJukebox.isChecked
            config.filters.removeKerrangTV = checkboxKerrangTV.isChecked
            config.filters.removeKissTV = checkboxKissTV.isChecked
            config.filters.removeLiteTV = checkboxLiteTV.isChecked
            config.filters.removeLoudTV = checkboxLoudTV.isChecked
            config.filters.removeMTV = checkboxMTV.isChecked
            config.filters.removePulse = checkboxPulse.isChecked
            config.filters.removeQVCMusic = checkboxQVCMusic.isChecked
            config.filters.removeRevolt = checkboxRevolt.isChecked
            config.filters.removeRIDEtv = checkboxRIDEtv.isChecked
            config.filters.removeStingray = checkboxStingray.isChecked
            config.filters.removeTheBox = checkboxTheBox.isChecked
            config.filters.removeTrace = checkboxTrace.isChecked
            config.filters.removeVevo = checkboxVevo.isChecked

// Save Music by Genre
            config.filters.removeAcappella = checkboxAcappella.isChecked
            config.filters.removeAcoustic = checkboxAcoustic.isChecked
            config.filters.removeAlternative = checkboxAlternative.isChecked
            config.filters.removeAmbient = checkboxAmbient.isChecked
            config.filters.removeBollywood = checkboxBollywood.isChecked
            config.filters.removeChildrensMusic = checkboxChildrensMusic.isChecked
            config.filters.removeChristianMusic = checkboxChristianMusic.isChecked
            config.filters.removeClassical = checkboxClassical.isChecked
            config.filters.removeClassicRock = checkboxClassicRock.isChecked
            config.filters.removeCountry = checkboxCountry.isChecked
            config.filters.removeDance = checkboxDance.isChecked
            config.filters.removeDisco = checkboxDisco.isChecked
            config.filters.removeEasyListening = checkboxEasyListening.isChecked
            config.filters.removeElectronic = checkboxElectronic.isChecked
            config.filters.removeFolk = checkboxFolk.isChecked
            config.filters.removeGospel = checkboxGospel.isChecked
            config.filters.removeGrunge = checkboxGrunge.isChecked
            config.filters.removeHardRock = checkboxHardRock.isChecked
            config.filters.removeHipHop = checkboxHipHop.isChecked
            config.filters.removeHolidayMusic = checkboxHolidayMusic.isChecked
            config.filters.removeIndie = checkboxIndie.isChecked
            config.filters.removeJazz = checkboxJazz.isChecked
            config.filters.removeKaraoke = checkboxKaraoke.isChecked
            config.filters.removeLatin = checkboxLatin.isChecked
            config.filters.removeLatinPop = checkboxLatinPop.isChecked
            config.filters.removeLofi = checkboxLofi.isChecked
            config.filters.removeMetal = checkboxMetal.isChecked
            config.filters.removeNewAge = checkboxNewAge.isChecked
            config.filters.removeOpera = checkboxOpera.isChecked
            config.filters.removePop = checkboxPop.isChecked
            config.filters.removePunk = checkboxPunk.isChecked
            config.filters.removeRnB = checkboxRnB.isChecked
            config.filters.removeRap = checkboxRap.isChecked
            config.filters.removeReggae = checkboxReggae.isChecked
            config.filters.removeRock = checkboxRock.isChecked
            config.filters.removeTechno = checkboxTechno.isChecked
            config.filters.removeTrance = checkboxTrance.isChecked
            config.filters.removeTriphop = checkboxTriphop.isChecked
            config.filters.removeWorldMusic = checkboxWorldMusic.isChecked

            // Save Young Children - Master
            config.filters.removeAllYoungChildren = checkboxRemoveAllYoungChildren.isChecked

// Save Young Children - Channels
            config.filters.removeNickJr = checkboxNickJr.isChecked
            config.filters.removeDisneyJunior = checkboxDisneyJunior.isChecked
            config.filters.removePBSKids = checkboxPBSKids.isChecked
            config.filters.removeUniversalKids = checkboxUniversalKids.isChecked
            config.filters.removeBabyTV = checkboxBabyTV.isChecked
            config.filters.removeBoomerang = checkboxBoomerang.isChecked
            config.filters.removeCartoonito = checkboxCartoonito.isChecked

// Save Young Children - Keywords
            config.filters.removePawPatrol = checkboxPawPatrol.isChecked
            config.filters.removeDoraTV = checkboxDoraTV.isChecked
            config.filters.removeMaxAndRuby = checkboxMaxAndRuby.isChecked
            config.filters.removeArthur = checkboxArthur.isChecked
            config.filters.removeTheWiggles = checkboxTheWiggles.isChecked
            config.filters.removeSpongeBob = checkboxSpongeBob.isChecked
            config.filters.removeRetroKid = checkboxRetroKid.isChecked
            config.filters.removeRetroToons = checkboxRetroToons.isChecked
            config.filters.removeTeletubbies = checkboxTeletubbies.isChecked
            config.filters.removeBobTheBuilder = checkboxBobTheBuilder.isChecked
            config.filters.removeInspectorGadget = checkboxInspectorGadget.isChecked
            config.filters.removeBarneyAndFriends = checkboxBarneyAndFriends.isChecked
            config.filters.removeBarbieAndFriends = checkboxBarbieAndFriends.isChecked
            config.filters.removeHotwheels = checkboxHotwheels.isChecked
            config.filters.removeMoonbug = checkboxMoonbug.isChecked
            config.filters.removeBlippi = checkboxBlippi.isChecked
            config.filters.removeCaillou = checkboxCaillou.isChecked
            config.filters.removeFiremanSam = checkboxFiremanSam.isChecked
            config.filters.removeBabyEinstein = checkboxBabyEinstein.isChecked
            config.filters.removeStrawberryShortcake = checkboxStrawberryShortcake.isChecked
            config.filters.removeHappyKids = checkboxHappyKids.isChecked
            config.filters.removeShaunTheSheep = checkboxShaunTheSheep.isChecked
            config.filters.removeRainbowRuby = checkboxRainbowRuby.isChecked
            config.filters.removeZoomoo = checkboxZoomoo.isChecked
            config.filters.removeRevAndRoll = checkboxRevAndRoll.isChecked
            config.filters.removeTgJunior = checkboxTgJunior.isChecked
            config.filters.removeDuckTV = checkboxDuckTV.isChecked
            config.filters.removeSensicalJr = checkboxSensicalJr.isChecked
            config.filters.removeKartoonChannel = checkboxKartoonChannel.isChecked
            config.filters.removeRyanAndFriends = checkboxRyanAndFriends.isChecked
            config.filters.removeNinjaKids = checkboxNinjaKids.isChecked
            config.filters.removeToonGoggles = checkboxToonGoggles.isChecked
            config.filters.removeKidoodle = checkboxKidoodle.isChecked
            config.filters.removePeppaPig = checkboxPeppaPig.isChecked
            config.filters.removeBbcKids = checkboxBBCKids.isChecked
            config.filters.removeLittleStarsUniverse = checkboxLittleStarsUniverse.isChecked
            config.filters.removeLittleAngelsPlayground = checkboxLittleAngelsPlayground.isChecked
            config.filters.removeBabyShark = checkboxBabyShark.isChecked
            config.filters.removeKidsAnime = checkboxKidsAnime.isChecked
            config.filters.removeGoGoGadget = checkboxGoGoGadget.isChecked
            config.filters.removeForeverKids = checkboxForeverKids.isChecked
            config.filters.removeLoolooKids = checkboxLoolooKids.isChecked
            config.filters.removePocoyo = checkboxPocoyo.isChecked
            config.filters.removeKetchupTV = checkboxKetchupTV.isChecked
            config.filters.removeSupertoonsTV = checkboxSupertoonsTV.isChecked
            config.filters.removeYaaas = checkboxYaaas.isChecked
            config.filters.removeSmurfTV = checkboxSmurfTV.isChecked
            config.filters.removeBratTV = checkboxBratTV.isChecked
            config.filters.removeCampSnoopy = checkboxCampSnoopy.isChecked
            config.filters.removeKidzBop = checkboxKidzBop.isChecked

            // Save Reality Shows - Master
            config.filters.removeAllRealityShows = checkboxRemoveAllRealityShows.isChecked

// Save Reality Shows - Channels
            config.filters.removeAE = checkboxAE.isChecked
            config.filters.removeBravo = checkboxBravo.isChecked
            config.filters.removeE = checkboxE.isChecked
            config.filters.removeLifetime = checkboxLifetime.isChecked
            config.filters.removeOWN = checkboxOWN.isChecked
            config.filters.removeTLC = checkboxTLC.isChecked
            config.filters.removeVH1 = checkboxVH1.isChecked
            config.filters.removeWEtv = checkboxWEtv.isChecked
            config.filters.removeMTVReality = checkboxMTVReality.isChecked

// Save Reality Shows - Keywords
            config.filters.remove90DayFiancee = checkbox90DayFiancee.isChecked
            config.filters.removeAmericanIdol = checkboxAmericanIdol.isChecked
            config.filters.removeAmazingRace = checkboxAmazingRace.isChecked
            config.filters.removeBigBrother = checkboxBigBrother.isChecked
            config.filters.removeChallenge = checkboxChallenge.isChecked
            config.filters.removeDancingWithTheStars = checkboxDancingWithTheStars.isChecked
            config.filters.removeDragRace = checkboxDragRace.isChecked
            config.filters.removeDuckDynasty = checkboxDuckDynasty.isChecked
            config.filters.removeFlipOrFlop = checkboxFlipOrFlop.isChecked
            config.filters.removeGordonRamsay = checkboxGordonRamsay.isChecked
            config.filters.removeHellSKitchen = checkboxHellSKitchen.isChecked
            config.filters.removeJerseyShore = checkboxJerseyShore.isChecked
            config.filters.removeJudge = checkboxJudge.isChecked
            config.filters.removeKardashians = checkboxKardashians.isChecked
            config.filters.removeLoveIsBlind = checkboxLoveIsBlind.isChecked
            config.filters.removeLoveItOrListIt = checkboxLoveItOrListIt.isChecked
            config.filters.removeLoveIsland = checkboxLoveIsland.isChecked
            config.filters.removeMasterchef = checkboxMasterchef.isChecked
            config.filters.removeMillionDollarListing = checkboxMillionDollarListing.isChecked
            config.filters.removeNosey = checkboxNosey.isChecked
            config.filters.removePerfectMatch = checkboxPerfectMatch.isChecked
            config.filters.removePawnStars = checkboxPawnStars.isChecked
            config.filters.removePropertyBrothers = checkboxPropertyBrothers.isChecked
            config.filters.removeQueerEye = checkboxQueerEye.isChecked
            config.filters.removeRealHousewives = checkboxRealHousewives.isChecked
            config.filters.removeSharkTank = checkboxSharkTank.isChecked
            config.filters.removeSinglesInferno = checkboxSinglesInferno.isChecked
            config.filters.removeStorageWars = checkboxStorageWars.isChecked
            config.filters.removeSurvivor = checkboxSurvivor.isChecked
            config.filters.removeTheBachelor = checkboxTheBachelor.isChecked
            config.filters.removeTheBachelorette = checkboxTheBachelorette.isChecked
            config.filters.removeTheMaskedSinger = checkboxTheMaskedSinger.isChecked
            config.filters.removeTheOsbournes = checkboxTheOsbournes.isChecked
            config.filters.removeTheUltimatum = checkboxTheUltimatum.isChecked
            config.filters.removeTheVoice = checkboxTheVoice.isChecked
            config.filters.removeTopModel = checkboxTopModel.isChecked
            config.filters.removeTeenMom = checkboxTeenMom.isChecked
            config.filters.removeTooHotToHandle = checkboxTooHotToHandle.isChecked
            config.filters.removeVanderpumpRules = checkboxVanderpumpRules.isChecked


            // Save updated config and backup
            ConfigManager.saveConfig(this, config)

            // Close and go back to main
            Toast.makeText(this, "Filters saved", Toast.LENGTH_SHORT).show()
            finish()
        }

        // Cancel button - just close
        buttonCancel.setOnClickListener {
            finish()
        }
    }
    private fun matchesWholeWord(text: String, word: String): Boolean {
        return Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }
    /**
     * Setup interactions between the checkboxes to prevent conflicts
     */
    private fun setupCheckboxInteractions() {
        // When Non-English is checked, suggest unchecking Non-Latin (since Non-English is stricter)
        removeNonEnglishCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && removeNonLatinCheckBox.isChecked) {
                Toast.makeText(
                    this,
                    "💡 Non-English filter is stricter than Non-Latin. Non-Latin filter disabled.",
                    Toast.LENGTH_SHORT
                ).show()
                removeNonLatinCheckBox.isChecked = false
            }

        }

        // When Non-Latin is checked and Non-English is already checked, warn user
        removeNonLatinCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && removeNonEnglishCheckBox.isChecked) {
                Toast.makeText(
                    this,
                    "💡 Non-English filter is already active (stricter than Non-Latin).",
                    Toast.LENGTH_SHORT
                ).show()
                removeNonLatinCheckBox.isChecked = false
            }
        }


    }

    /**
     * Helper to check if config.json or backup.json exists.
     */
    private fun configExists(): Boolean {
        val configFile = getFileStreamPath("config.json")
        val backupFile = getFileStreamPath("config_backup.json")
        return configFile.exists() || backupFile.exists()
    }
}