package com.touchbase.agent.ui.enrollment

/**
 * Ghana administrative data + option lists used by the M-KOPA style
 * "Start Application" flow (region -> district dropdowns, languages, statuses).
 */
object GhanaGeo {

    /** 16 regions of Ghana -> major districts (curated, ends with "Other"). */
    val REGIONS: LinkedHashMap<String, List<String>> = linkedMapOf(
        "Ahafo" to listOf("Asunafo North", "Asunafo South", "Asutifi North", "Asutifi South", "Tano North", "Tano South", "Other"),
        "Ashanti" to listOf("Kumasi Metropolitan", "Obuasi Municipal", "Ejisu", "Asokore Mampong", "Bekwai", "Bosome Freho", "Atwima Kwanwoma", "Atwima Nwabiagya", "Mampong Municipal", "Offinso Municipal", "Konongo", "Other"),
        "Bono" to listOf("Sunyani Municipal", "Sunyani West", "Berekum East", "Berekum West", "Dormaa Central", "Dormaa East", "Dormaa West", "Jaman North", "Jaman South", "Tain", "Wenchi", "Other"),
        "Bono East" to listOf("Techiman Municipal", "Kintampo North", "Kintampo South", "Nkoranza North", "Nkoranza South", "Atebubu-Amantin", "Pru East", "Pru West", "Sene East", "Sene West", "Other"),
        "Central" to listOf("Cape Coast Metropolitan", "Mfantsiman Municipal", "Awutu Senya East", "Awutu Senya West", "Gomoa East", "Gomoa West", "Effutu Municipal", "Agona West", "Agona East", "Assin North", "Assin South", "Twifo Atti Morkwa", "Komenda-Edina-Eguafo-Abirem", "Other"),
        "Eastern" to listOf("New Juaben South", "New Juaben North", "Suhum", "Nsawam Adoagyiri", "Akuapim South", "Akuapim North", "Akwapim (Okere)", "Yilo Krobo", "Lower Manya Krobo", "Upper Manya Krobo", "Birim Central", "Birim North", "Birim South", "Akyemansa", "West Akim", "East Akim", "Kwahu East", "Kwahu South", "Kwahu West", "Fanteakwa North", "Fanteakwa South", "Other"),
        "Greater Accra" to listOf("Accra Metropolitan", "Tema Metropolitan", "Tema West", "Ledzokuku", "Krowor", "Adentan", "Ashaiman", "La-Nkwantanang-Madina", "Ga Central", "Ga East", "Ga North", "Ga South", "Ga West", "Kpone-Katamanso", "Ningo-Prampram", "Shai-Osudoku", "Ablekuma Central", "Ablekuma North", "Ablekuma West", "Ayawaso Central", "Ayawaso East", "Ayawaso North", "Ayawaso West", "Okaikwei North", "Weija-Gbawe", "Other"),
        "North East" to listOf("Mamprugu Moagduri", "West Mamprusi", "East Mamprusi", "Bunkpurugu-Nakpanduri", "Yunyoo-Nasuan", "Chereponi", "Other"),
        "Northern" to listOf("Tamale Metropolitan", "Sagnarigu", "Savelugu", "Nanton", "Tolon", "Kumbungu", "Karaga", "Gushegu", "Yendi", "Mion", "Saboba", "Zabzugu", "Tatale-Sanguli", "Nanumba North", "Nanumba South", "Kpandai", "Other"),
        "Oti" to listOf("Krachi East", "Krachi West", "Krachi Nchumuru", "Nkwanta North", "Nkwanta South", "Biakoye", "Jasikan", "Kadjebi", "Other"),
        "Savannah" to listOf("Damongo", "West Gonja", "North Gonja", "Central Gonja", "East Gonja", "Bole", "Sawla-Tuna-Kalba", "North East Gonja", "Other"),
        "Upper East" to listOf("Bolgatanga Municipal", "Bawku Municipal", "Bawku West", "Binduri", "Pusiga", "Garu", "Tempane", "Kassena-Nankana Municipal", "Kassena-Nankana West", "Builsa North", "Builsa South", "Talensi", "Nabdam", "Bongo", "Other"),
        "Upper West" to listOf("Wa Municipal", "Wa East", "Wa West", "Nadowli-Kaleo", "Daffiama-Bussie-Issa", "Jirapa", "Lawra", "Nandom", "Lambussie", "Sissala East", "Sissala West", "Other"),
        "Volta" to listOf("Ho Municipal", "Ho West", "Adaklu", "Agotime-Ziope", "Hohoe Municipal", "Kpando Municipal", "Ketu North", "Ketu South", "Keta Municipal", "Anloga", "Akatsi North", "Akatsi South", "Central Tongu", "North Tongu", "South Tongu", "South Dayi", "North Dayi", "Afadzato South", "Other"),
        "Western" to listOf("Sekondi-Takoradi Metropolitan", "Effia-Kwesimintsim", "Shama", "Ahanta West", "Nzema East", "Ellembelle", "Jomoro", "Wassa East", "Mpohor", "Tarkwa-Nsuaem", "Prestea-Huni Valley", "Amenfi Central", "Amenfi East", "Amenfi West", "Wassa Amenfi West", "Other"),
        "Western North" to listOf("Sefwi Wiawso", "Bibiani-Anhwiaso-Bekwai", "Sefwi Akontombra", "Suaman", "Bodi", "Juaboso", "Bia East", "Bia West", "Aowin", "Other")
    )

    val REGION_NAMES: List<String> get() = REGIONS.keys.toList()

    fun districtsFor(region: String): List<String> = REGIONS[region] ?: emptyList()

    val LANGUAGES = listOf("English", "Twi", "Ewe", "Ga", "Dagbani", "Hausa", "Nzema", "Fante", "French")

    val MARITAL_STATUSES = listOf("Single", "Married", "Divorced", "Widowed")

    val EMPLOYMENT_STATUSES = listOf("Student", "Employed", "Self-employed", "Trader", "Farmer", "Business owner", "Unemployed")
}
