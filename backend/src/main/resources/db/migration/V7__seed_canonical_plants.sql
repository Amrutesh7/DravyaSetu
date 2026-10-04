INSERT INTO plants (
    plant_code,
    plant_identifier,
    common_name,
    ayurvedic_regional_names,
    scientific_name
)
VALUES
    (
        'PLANT_001',
        'Aloevera',
        'Aloe Vera',
        ARRAY['Kumari', 'Ghritakumari']::TEXT[],
        'Aloe vera (L.) Burm.f.'
    ),
    (
        'PLANT_002',
        'Amla',
        'Indian Gooseberry',
        ARRAY['Amalaki', 'Nelli']::TEXT[],
        'Phyllanthus emblica L.'
    ),
    (
        'PLANT_003',
        'Amruta_Balli',
        'Heart-leaved Moonseed',
        ARRAY['Guduchi', 'Amrita', 'Amrutaballi']::TEXT[],
        'Tinospora cordifolia (Willd.) Miers'
    ),
    (
        'PLANT_004',
        'Arali',
        'Sacred Fig / Peepal Tree',
        ARRAY['Ashwatha', 'Arali Mara']::TEXT[],
        'Ficus religiosa L.'
    ),
    (
        'PLANT_005',
        'Ashoka',
        'Ashoka Tree',
        ARRAY['Asoka', 'Ashokamara']::TEXT[],
        'Saraca asoca (Roxb.) Willd.'
    ),
    (
        'PLANT_006',
        'Ashwagandha',
        'Indian Ginseng / Winter Cherry',
        ARRAY['Ashwagandha', 'Asgandh']::TEXT[],
        'Withania somnifera (L.) Dunal'
    ),
    (
        'PLANT_007',
        'Betel',
        'Betel Leaf',
        ARRAY['Tambula', 'Vlledale']::TEXT[],
        'Piper betle L.'
    ),
    (
        'PLANT_008',
        'Betel_Nut',
        'Areca Nut / Betel Nut Palm',
        ARRAY['Puga', 'Adike']::TEXT[],
        'Areca catechu L.'
    ),
    (
        'PLANT_009',
        'Brahmi',
        'Water Hyssop',
        ARRAY['Brahmi', 'Saraswati Plant']::TEXT[],
        'Bacopa monnieri (L.) Wettst.'
    ),
    (
        'PLANT_010',
        'Castor',
        'Castor Bean Plant',
        ARRAY['Eranda', 'Haralu']::TEXT[],
        'Ricinus communis L.'
    ),
    (
        'PLANT_011',
        'Curry_Leaf',
        'Curry Leaf',
        ARRAY['Kaidarya', 'Karivepaku', 'Kadi Patta']::TEXT[],
        'Murraya koenigii (L.) Spreng.'
    ),
    (
        'PLANT_012',
        'Doddapatre',
        'Indian Borage / Mexican Mint',
        ARRAY['Karpooravalli', 'Doddapatre']::TEXT[],
        'Plectranthus amboinicus (Lour.) Spreng.'
    ),
    (
        'PLANT_013',
        'Ekka',
        'Crown Flower / Giant Milkweed',
        ARRAY['Arka', 'Ekka Gida']::TEXT[],
        'Calotropis gigantea (L.) Dryand. / Calotropis procera'
    ),
    (
        'PLANT_014',
        'Ganike',
        'Black Nightshade / Makoi',
        ARRAY['Kakamachi', 'Ganike Gida']::TEXT[],
        'Solanum nigrum L.'
    ),
    (
        'PLANT_015',
        'Henna',
        'Henna',
        ARRAY['Madayantika', 'Mehendi', 'Goranti']::TEXT[],
        'Lawsonia inermis L.'
    ),
    (
        'PLANT_016',
        'Hibiscus',
        'Shoe Flower / China Rose',
        ARRAY['Japa Pushpa', 'Dasavala']::TEXT[],
        'Hibiscus rosa-sinensis L.'
    ),
    (
        'PLANT_017',
        'Honge',
        'Indian Beech Tree',
        ARRAY['Karanja', 'Honge Mara']::TEXT[],
        'Millettia pinnata (L.) Panigrahi'
    ),
    (
        'PLANT_018',
        'Insulin',
        'Insulin Plant',
        ARRAY['Fiery Costus', 'Spiral Flag']::TEXT[],
        'Chamaecostus cuspidatus (Nees & Mart.) C.D.Specht & D.W.Stevens'
    ),
    (
        'PLANT_019',
        'Jasmine',
        'Jasmine',
        ARRAY['Jati', 'Mallige']::TEXT[],
        'Jasminum officinale L. / Jasminum sambac'
    ),
    (
        'PLANT_020',
        'Lemon',
        'Lemon',
        ARRAY['Nimbuka', 'Limbe']::TEXT[],
        'Citrus limon (L.) Osbeck'
    ),
    (
        'PLANT_021',
        'Lemon_grass',
        'Lemongrass',
        ARRAY['Bhutrina', 'Majjige Pullu']::TEXT[],
        'Cymbopogon citratus (DC.) Stapf'
    ),
    (
        'PLANT_022',
        'Mango',
        'Mango Tree',
        ARRAY['Amra', 'Mavina Mara']::TEXT[],
        'Mangifera indica L.'
    ),
    (
        'PLANT_023',
        'Mint',
        'Mint / Spearmint',
        ARRAY['Pudina']::TEXT[],
        'Mentha spicata L. / Mentha piperita'
    ),
    (
        'PLANT_024',
        'Nagadali',
        'Common Rue / Herb-of-Grace',
        ARRAY['Nagadali', 'Sadapaha']::TEXT[],
        'Ruta graveolens L.'
    ),
    (
        'PLANT_025',
        'Neem',
        'Neem Tree / Indian Lilac',
        ARRAY['Nimba', 'Bevinamara']::TEXT[],
        'Azadirachta indica A.Juss.'
    ),
    (
        'PLANT_026',
        'Nithyapushpa',
        'Madagascar Periwinkle',
        ARRAY['Sadabahar', 'Nithyapushpa']::TEXT[],
        'Catharanthus roseus (L.) G.Don'
    ),
    (
        'PLANT_027',
        'Nooni',
        'Noni / Great Morinda / Indian Mulberry',
        ARRAY['Achuka', 'Noni']::TEXT[],
        'Morinda citrifolia L.'
    ),
    (
        'PLANT_028',
        'Pappaya',
        'Papaya Tree',
        ARRAY['Erandakarkati', 'Pappayi']::TEXT[],
        'Carica papaya L.'
    ),
    (
        'PLANT_029',
        'Pepper',
        'Black Pepper',
        ARRAY['Maricha', 'Menasu']::TEXT[],
        'Piper nigrum L.'
    ),
    (
        'PLANT_030',
        'Pomegranate',
        'Pomegranate',
        ARRAY['Dadima', 'Dalimbe']::TEXT[],
        'Punica granatum L.'
    ),
    (
        'PLANT_031',
        'Raktachandini',
        'Red Sandalwood / Red Sanders',
        ARRAY['Rakta Chandana', 'Agaru']::TEXT[],
        'Pterocarpus santalinus L.f.'
    ),
    (
        'PLANT_032',
        'Rose',
        'Rose',
        ARRAY['Shatapatri', 'Gulabi']::TEXT[],
        'Rosa indica L. / Rosa damascena'
    ),
    (
        'PLANT_033',
        'Tulasi',
        'Holy Basil',
        ARRAY['Tulasi', 'Surasa']::TEXT[],
        'Ocimum sanctum L.'
    ),
    (
        'PLANT_034',
        'Wood_sorel',
        'Indian Sorrel / Creeping Wood Sorrel',
        ARRAY['Changeri', 'Huli Soppu']::TEXT[],
        'Oxalis corniculata L.'
    )
    ON CONFLICT (plant_code)
DO UPDATE SET
    plant_identifier = EXCLUDED.plant_identifier,
           common_name = EXCLUDED.common_name,
           ayurvedic_regional_names = EXCLUDED.ayurvedic_regional_names,
           scientific_name = EXCLUDED.scientific_name;