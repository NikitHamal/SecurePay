/**
 * Ghana regions -> districts and the option lists used by the M-KOPA style
 * "Start Application" wizard (mirrors GhanaGeo.kt in the agent app).
 */
export const REGIONS: Record<string, string[]> = {
  Ahafo: ['Asunafo North', 'Asunafo South', 'Asutifi North', 'Asutifi South', 'Tano North', 'Tano South', 'Other'],
  Ashanti: ['Kumasi Metropolitan', 'Obuasi Municipal', 'Ejisu', 'Asokore Mampong', 'Bekwai', 'Bosome Freho', 'Atwima Kwanwoma', 'Atwima Nwabiagya', 'Mampong Municipal', 'Offinso Municipal', 'Konongo', 'Other'],
  Bono: ['Sunyani Municipal', 'Sunyani West', 'Berekum East', 'Berekum West', 'Dormaa Central', 'Dormaa East', 'Dormaa West', 'Jaman North', 'Jaman South', 'Tain', 'Wenchi', 'Other'],
  'Bono East': ['Techiman Municipal', 'Kintampo North', 'Kintampo South', 'Nkoranza North', 'Nkoranza South', 'Atebubu-Amantin', 'Pru East', 'Pru West', 'Sene East', 'Sene West', 'Other'],
  Central: ['Cape Coast Metropolitan', 'Mfantsiman Municipal', 'Awutu Senya East', 'Awutu Senya West', 'Gomoa East', 'Gomoa West', 'Effutu Municipal', 'Agona West', 'Agona East', 'Assin North', 'Assin South', 'Twifo Atti Morkwa', 'Komenda-Edina-Eguafo-Abirem', 'Other'],
  Eastern: ['New Juaben South', 'New Juaben North', 'Suhum', 'Nsawam Adoagyiri', 'Akuapim South', 'Akuapim North', 'Yilo Krobo', 'Lower Manya Krobo', 'Upper Manya Krobo', 'Birim Central', 'Birim North', 'Birim South', 'Akyemansa', 'West Akim', 'East Akim', 'Kwahu East', 'Kwahu South', 'Kwahu West', 'Fanteakwa North', 'Fanteakwa South', 'Other'],
  'Greater Accra': ['Accra Metropolitan', 'Tema Metropolitan', 'Tema West', 'Ledzokuku', 'Krowor', 'Adentan', 'Ashaiman', 'La-Nkwantanang-Madina', 'Ga Central', 'Ga East', 'Ga North', 'Ga South', 'Ga West', 'Kpone-Katamanso', 'Ningo-Prampram', 'Shai-Osudoku', 'Ablekuma Central', 'Ablekuma North', 'Ablekuma West', 'Ayawaso Central', 'Ayawaso East', 'Ayawaso North', 'Ayawaso West', 'Okaikwei North', 'Weija-Gbawe', 'Other'],
  'North East': ['Mamprugu Moagduri', 'West Mamprusi', 'East Mamprusi', 'Bunkpurugu-Nakpanduri', 'Yunyoo-Nasuan', 'Chereponi', 'Other'],
  Northern: ['Tamale Metropolitan', 'Sagnarigu', 'Savelugu', 'Nanton', 'Tolon', 'Kumbungu', 'Karaga', 'Gushegu', 'Yendi', 'Mion', 'Saboba', 'Zabzugu', 'Tatale-Sanguli', 'Nanumba North', 'Nanumba South', 'Kpandai', 'Other'],
  Oti: ['Krachi East', 'Krachi West', 'Krachi Nchumuru', 'Nkwanta North', 'Nkwanta South', 'Biakoye', 'Jasikan', 'Kadjebi', 'Other'],
  Savannah: ['Damongo', 'West Gonja', 'North Gonja', 'Central Gonja', 'East Gonja', 'Bole', 'Sawla-Tuna-Kalba', 'North East Gonja', 'Other'],
  'Upper East': ['Bolgatanga Municipal', 'Bawku Municipal', 'Bawku West', 'Binduri', 'Pusiga', 'Garu', 'Tempane', 'Kassena-Nankana Municipal', 'Kassena-Nankana West', 'Builsa North', 'Builsa South', 'Talensi', 'Nabdam', 'Bongo', 'Other'],
  'Upper West': ['Wa Municipal', 'Wa East', 'Wa West', 'Nadowli-Kaleo', 'Daffiama-Bussie-Issa', 'Jirapa', 'Lawra', 'Nandom', 'Lambussie', 'Sissala East', 'Sissala West', 'Other'],
  Volta: ['Ho Municipal', 'Ho West', 'Adaklu', 'Agotime-Ziope', 'Hohoe Municipal', 'Kpando Municipal', 'Ketu North', 'Ketu South', 'Keta Municipal', 'Anloga', 'Akatsi North', 'Akatsi South', 'Central Tongu', 'North Tongu', 'South Tongu', 'South Dayi', 'North Dayi', 'Afadzato South', 'Other'],
  Western: ['Sekondi-Takoradi Metropolitan', 'Effia-Kwesimintsim', 'Shama', 'Ahanta West', 'Nzema East', 'Ellembelle', 'Jomoro', 'Wassa East', 'Mpohor', 'Tarkwa-Nsuaem', 'Prestea-Huni Valley', 'Amenfi Central', 'Amenfi East', 'Amenfi West', 'Other'],
  'Western North': ['Sefwi Wiawso', 'Bibiani-Anhwiaso-Bekwai', 'Sefwi Akontombra', 'Suaman', 'Bodi', 'Juaboso', 'Bia East', 'Bia West', 'Aowin', 'Other']
};

export const REGION_NAMES = Object.keys(REGIONS);

export const LANGUAGES = ['English', 'Twi', 'Ewe', 'Ga', 'Dagbani', 'Hausa', 'Nzema', 'Fante', 'French'];
export const MARITAL_STATUSES = ['Single', 'Married', 'Divorced', 'Widowed'];
export const EMPLOYMENT_STATUSES = ['Student', 'Employed', 'Self-employed', 'Trader', 'Farmer', 'Business owner', 'Unemployed'];
export const ID_TYPES = ['Ghana Card', 'National ID', 'Voter ID', 'Passport', "Driver's Licence"];
export const RELATIONS = ['Spouse', 'Parent', 'Sibling', 'Child', 'Relative', 'Friend', 'Colleague'];
export const GENDERS = ['Male', 'Female'];
