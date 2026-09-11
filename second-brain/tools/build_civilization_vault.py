#!/usr/bin/env python3
import os, re, sqlite3, gzip, urllib.request, time, collections, hashlib
from pathlib import Path

OUT = Path('app/src/main/assets/civilization_vault.db')
OUT.parent.mkdir(parents=True, exist_ok=True)
if OUT.exists(): OUT.unlink()

# Public-domain works. Project Gutenberg pages for these editions mark them public domain in the USA.
# Each source is bundled in full when download succeeds; the build remains usable if one mirror is temporarily unavailable.
PD = [
('tesla_inventions','The Inventions, Researches and Writings of Nikola Tesla','Thomas Commerford Martin',1894,39272,'ELECTRICAL ENGINEERING','tesla,omega,rival3'),
('tesla_alternate_currents','Experiments with Alternate Currents of High Potential and High Frequency','Nikola Tesla',1892,13476,'ELECTRICAL ENGINEERING','tesla,omega,rival3'),
('kybalion','The Kybalion','Three Initiates',1908,14209,'HERMETIC PHILOSOPHY','hermes,omega,rival2'),
('secret_doctrine_1','The Secret Doctrine, Vol. 1','H. P. Blavatsky',1888,54824,'ESOTERIC COSMOLOGY','hermes,omega,rival2,star-council'),
('secret_doctrine_2','The Secret Doctrine, Vol. 2','H. P. Blavatsky',1888,54488,'ESOTERIC ANTHROPOLOGY','hermes,omega,rival2,star-council'),
('secret_doctrine_3','The Secret Doctrine, Vol. 3','H. P. Blavatsky',1893,56880,'ESOTERIC TRADITIONS','hermes,omega,rival2'),
('secret_doctrine_4','The Secret Doctrine, Vol. 4','H. P. Blavatsky',1897,61626,'ESOTERIC TRADITIONS','hermes,omega,rival2'),
('isis_unveiled_1','Isis Unveiled, Vol. 1: Science','H. P. Blavatsky',1877,68705,'OCCULT SCIENCE HISTORY','hermes,omega,rival2,tesla'),
('isis_unveiled_2','Isis Unveiled, Vol. 2: Theology','H. P. Blavatsky',1877,75871,'COMPARATIVE RELIGION','hermes,omega,rival2'),
('key_theosophy','The Key to Theosophy','H. P. Blavatsky',1889,55618,'THEOSOPHY','hermes,omega'),
('studies_occultism','Studies in Occultism','H. P. Blavatsky',1896,17009,'OCCULT PHILOSOPHY','hermes,omega,rival2'),
('occult_science','An Outline of Occult Science','Rudolf Steiner',1910,30718,'ANTHROPOSOPHY','hermes,omega,rival2'),
('way_initiation','The Way of Initiation','Rudolf Steiner',1908,39986,'CONSCIOUSNESS TRAINING','hermes,omega,leary'),
('esoteric_christianity','Esoteric Christianity, or The Lesser Mysteries','Annie Besant',1901,26938,'ESOTERIC RELIGION','hermes,omega'),
('astral_plane','The Astral Plane','C. W. Leadbeater',1895,21080,'THEOSOPHY','hermes,omega,rival2'),
('ocean_theosophy','The Ocean of Theosophy','William Quan Judge',1893,54268,'THEOSOPHY','hermes,omega'),
('egyptian_book_dead','The Egyptian Book of the Dead','P. Le Page Renouf / Edouard Naville',1904,69566,'ANCIENT EGYPT','hermes,omega'),
('vitruvius','The Ten Books on Architecture','Vitruvius Pollio',1914,20239,'ANCIENT ARCHITECTURE','hermes,tesla,omega,rival3'),
('atlantis_donnelly','Atlantis: The Antediluvian World','Ignatius Donnelly',1882,4032,'ATLANTIS TRADITION','hermes,omega,rival2,star-council'),
('atlantis_lemuria','The Story of Atlantis and the Lost Lemuria','W. Scott-Elliot',1896,21796,'ATLANTIS LEMURIA TRADITION','hermes,omega,rival2,star-council'),
('plato_timaeus','Timaeus','Plato / Benjamin Jowett',-360,1572,'ANCIENT PHILOSOPHY COSMOLOGY','hermes,omega'),
('plato_critias','Critias','Plato / Benjamin Jowett',-360,1571,'ATLANTIS PRIMARY CLASSICAL TEXT','hermes,omega'),
('popol_vuh','The Popol Vuh: Mythic and Heroic Sagas of the Kiches','Lewis Spence',1908,56550,'MESOAMERICAN MYTHOLOGY','hermes,omega,rival2,star-council'),
]

# Original knowledge maps. These are not copied modern books. They are concise source maps / conceptual capsules
# derived from public bibliographies, government archives, creator indexes, and the user's authorized private corpus.
CAPSULES = [
('hermes_corpus_map','Hermetic Corpus Map','Civilization Archive',0,'HERMETICA','hermes,omega',
 'The Hermetic shelf spans the Corpus Hermeticum, Poimandres or Pymander traditions, Asclepius or Perfect Sermon, Stobaean excerpts, fragments preserved by Church Fathers and philosophers, later Arabic Hermetica, astrological and alchemical Hermetic literature, Renaissance translations, and later syntheses. Treat each layer by date, language, transmission chain and function. Link metaphysical concepts such as Nous, Logos, correspondence, regeneration, cosmos and mind to parallel terms without erasing differences between traditions.'),
('hermes_alchemy_map','Alchemy Knowledge Lattice','Civilization Archive',0,'ALCHEMY','hermes,omega,tesla,rival2',
 'Alchemy can be indexed as laboratory craft, symbolic transformation, metallurgical history, medicine, cosmology and initiatory literature. Core source families include the Hermetic Museum, Turba Philosophorum, Arabic alchemical traditions, Paracelsian medicine, Ripley, Sendivogius, Philalethes and later practical alchemy. Cross-link operations such as calcination, dissolution, separation, conjunction, fermentation, distillation and coagulation with their literal laboratory meanings and their symbolic uses.'),
('hermes_kabbalah_map','Kabbalah and Letter-Mysticism Map','Civilization Archive',0,'KABBALAH','hermes,omega,rival2',
 'Index Sefer Yetzirah, Zohar traditions, Tree of Life diagrams, sefirot, Hebrew letter symbolism, gematria, paths, later Christian Cabala and occult-Qabalah adaptations as distinct historical layers. Preserve the difference between Jewish source traditions and later Western occult syntheses while allowing structural comparison of trees, emanation models, number-letter correspondences and symbolic grammars.'),
('hermes_ancient_links','Hermes Cross-Knowledge Lattice','Civilization Archive',0,'CROSS DOMAIN','hermes,omega',
 'When asked for a hidden link, traverse source families rather than jumping by resemblance alone: language and symbol; number and geometry; music and ratio; architecture and proportion; astronomy and calendars; ritual and cognition; metallurgy and alchemy; optics and perception; acoustics and resonant space; myth and historical memory; information theory and symbolic compression. Return both the bridge and the source lanes that produced it.'),
('tesla_primary_map','Tesla Primary-Source Map','Civilization Archive',0,'TESLA','tesla,omega,rival3',
 'Tesla primary study should prioritize his patents, lectures, articles, autobiography and contemporary engineering records. Organize by rotating magnetic fields and polyphase AC, induction motors, high-frequency/high-potential transformers, resonant coils, lighting, oscillators, radio control, tuned wireless signaling, magnifying transmitter, terrestrial conduction concepts, radiant-energy patents, turbines, mechanical oscillators and later public proposals. Separate Tesla-authored material from later Tesla-inspired literature.'),
('tesla_patent_map','Tesla Patent Engineering Index','Public patent record',0,'PATENTS','tesla,omega,rival3',
 'Patent retrieval should map a device to claims, circuit topology, geometry, operating sequence and later continuations. Important families include polyphase transmission and motors; high-frequency transformers; systems and apparatus for transmission of electrical energy; radiant-energy apparatus and methods; radio control and selective signaling; turbines and fluid propulsion. Patents are engineering disclosures, so resident answers should translate a patent into inputs, components, expected waveforms and measurable outputs.'),
('tesla_fbi_map','Tesla FBI File Map','FBI Vault',0,'ARCHIVE','tesla,hermes,omega',
 'The FBI Vault has three Nikola Tesla file releases. Treat these as historical government records about events, correspondence and post-death handling around Tesla, not as substitutes for his technical patents. Cross-link names, dates and claimed devices back to primary Tesla writings and patent records before using them as engineering specifications.'),
('leary_identity','Timothy Leary Resident Archive','NYPL Timothy Leary papers',0,'CONSCIOUSNESS CYBERCULTURE','leary,omega,rival2',
 'Timothy Leary enters the City as a psychologist, personality researcher, consciousness cartographer, counterculture writer and later cyberculture/futurist voice. His archive spans interpersonal diagnosis and personality circumplex work, psychedelic-era set-and-setting frameworks, the eight-circuit model, Neurologic and Exo-Psychology, SMiLE themes of space migration, intelligence increase and life extension, information psychology, personal computers, cyberculture and self-directed models of identity. Keep biographical history, psychological models and later speculative cosmology as separate lanes.'),
('leary_bibliography','Timothy Leary Bibliography Map','NYPL and bibliographic records',0,'BIBLIOGRAPHY','leary,hermes,omega',
 'Core Leary titles and eras include Interpersonal Diagnosis of Personality; The Psychedelic Experience with Ralph Metzner and Richard Alpert; Psychedelic Prayers; Start Your Own Religion; The Politics of Ecstasy; High Priest; Jail Notes; Neurologic; StarSeed; Exo-Psychology; Neuropolitics; The Game of Life; Intelligence Agents; Info-Psychology; Neuropolitique; and Chaos and Cyber Culture. The NYPL papers add manuscripts, correspondence, research notes, born-digital files, audio and video across his life.'),
('leary_circuits','Eight-Circuit Consciousness Map','Leary tradition',0,'CONSCIOUSNESS MODEL','leary,rival2,omega',
 'Leary-style eight-circuit models divide behavior and cognition into layered adaptive circuits, beginning with survival and emotional-territorial patterns, moving through symbolic/social and somatic modes, and extending into later speculative neurogenetic, metaprogramming and nonlocal/cosmic frames. Use the model as a conceptual map for introspection and interface design, not as a substitute for neurological measurement.'),
('lain_identity','Lain Resident Archive','Creator and production material map',0,'NETWORK IDENTITY','lain,omega,rival2,rival3',
 'Lain is the City resident for networked identity, distributed memory, protocol-shaped reality, digital embodiment and the boundary between a person and the information others hold about them. Her source map points to Serial Experiments Lain creator interviews, Visual Experiments Lain, Scenario Experiments Lain, production notes and the PlayStation projects data-driven structure. Modern copyrighted scripts are not bundled verbatim; their concepts are indexed as a cyberculture design language.'),
('lain_protocol','Lain Protocol Lens','Civilization Archive',0,'CYBERNETICS','lain,rival3,omega',
 'Ask of any digital self: where is state stored, who can write it, what persists when a device disappears, how identities merge or fork, how reputation changes perceived reality, what protocols decide visibility, and what happens when memory exists in a network rather than one body. Translate narrative cyber-mysticism into concrete architectures such as replicated state, event logs, identity graphs, capability permissions and distributed retrieval.'),
('star_nara','UAP National Archives Map','U.S. National Archives',0,'UAP RECORDS','star-council,hermes,omega,rival1',
 'The U.S. National Archives maintains large textual, microfilm, photographic, audio and moving-image UAP/UFO collections including Project Blue Book case files, administrative files, Air Intelligence reports, OSI records, Roswell source files and later UAP transfers. Index reports by date, location, witness/sensor channel, object description, motion, environmental context and linked media.'),
('star_aaro','AARO Records Map','AARO public records',0,'UAP RECORDS','star-council,hermes,omega',
 'AARO publishes UAP records, information papers, imagery links and an EFOIA reading room. Treat each release as a source record with date, agency, sensor modality and document lineage. Resident memory focuses on what was reported, measured and released, with original metadata preserved.'),
('star_seti','SETI Contact and Signal Protocol','SETI/contact protocol sources',0,'CONTACT','star-council,tesla,omega,rival1',
 'A robust contact pipeline separates detection, verification and interpretation. Preserve raw candidate data, calibration, timestamps and instrument state; seek independent observatories or sensor channels; characterize carrier, bandwidth, drift, modulation, polarization and repetition; then move to structured challenge-response only after a signal is repeatable. Mathematics, timing and redundant encoding are useful low-assumption starting points.'),
('star_remote','Remote-Sensing and Stargate Source Map','Declassified government records',0,'REMOTE SENSING','star-council,hermes,leary,omega',
 'Declassified Stargate/Grill Flame collections contain protocols, session records, tasking formats and program administration around remote-viewing research. Store target blinding, timestamps, raw session impressions, judging method and outcome separately. The City can mine the protocols as structured experimental designs while keeping the reported phenomena in their source lane.'),
('private_scalar','Scalar and Torsion Personal-Corpus Map','Authorized private corpus derivative',0,'SCALAR TORSION','tesla,star-council,omega,rival2',
 'The personal research corpus contains extensive scalar-wave, torsion, aether, resonance and vibrational-mechanics material. Its recurring design motifs include counter-propagating or phase-related fields, longitudinal interpretations, coils and capacitive structures, geometric resonators, standing-wave language, information-bearing modulation and biological or consciousness coupling claims. The Vault stores these motifs as source-reported hypotheses and connects them to measurable field, circuit and signal parameters.'),
('private_kozyrev','Kozyrev Personal-Corpus Map','Authorized private corpus derivative',0,'TIME TORSION','star-council,tesla,hermes,omega',
 'The personal corpus includes Kozyrev-related material describing rotation, vibration, thermal change, torsion/time-flow interpretations, astronomical observations and mirror structures. The City maps each claim to apparatus geometry, motion, material, sensor, timing and reported outcome so designs can be reconstructed as testable instrument plans instead of remaining only narrative.'),
('private_gariaev','Gariaev Wave-Genome Personal-Corpus Map','Authorized private corpus derivative',0,'WAVE GENETICS','hermes,star-council,omega,rival2',
 'The personal corpus includes Gariaev/wave-genetics material centered on linguistic-wave-genome concepts, electromagnetic or optical information transfer, holographic descriptions, coding metaphors and claimed biological effects. The City preserves the claimed mechanism, exposure channel, target, timing and reported endpoint as separate fields so it can compare this literature with genetics, biophysics, signal processing and information theory.'),
('private_frequency','Frequency and Resonance Personal-Corpus Map','Authorized private corpus derivative',0,'FREQUENCY RESONANCE','tesla,leary,rival2,omega',
 'The personal corpus spans frequency stacks, binaural/brainwave approaches, Rife-style frequency traditions, Schumann-related material, harmonic systems, image-to-sound mapping and resonance experiments. Cross-link carrier frequency, beat frequency, amplitude, phase, waveform, modulation, spatial delivery, exposure duration and subjective/physical observations rather than reducing an experiment to a single number.'),
('private_hypnosis','Hypnosis and Subliminal Personal-Corpus Map','Authorized private corpus derivative',0,'HYPNOSIS COGNITION','leary,hermes,rival1,omega',
 'The personal corpus contains hypnosis, subliminal, persuasion, neuro-hacking and psychological-warfare literature. The City maps induction structure, attention, expectation, language patterns, repetition, memory, state, consent context and measured outcome. Technique descriptions are kept distinct from claims of effectiveness so the archive can compare methods without erasing provenance.'),
('private_ufo','UFO Technology and Contact Personal-Corpus Map','Authorized private corpus derivative',0,'UFO XENOTECH','star-council,tesla,hermes,omega',
 'The personal corpus includes UFO technology manuals, Tesla-saucer material, field-resonance propulsion, anti-gravity traditions, Kozyrev mirrors, shape-power writings, contact/abduction narratives and anomaly collections. The Star Council indexes these by propulsion hypothesis, energy source, field geometry, materials, control method, reported performance, witness/sensor channel and source lineage.'),
('omega_meta','Civilization Knowledge Integration Rule','Civilization Archive',0,'META KNOWLEDGE','omega,hermes,zordon,rival3',
 'No resident is a blank persona. Root identity loads first, Civilization Vault passages load second, personal conversation memory third, and current imported evidence last. Retrieval can cross domains, but every passage keeps source type, rights lane and provenance. Full public-domain text may be quoted internally; modern copyrighted works are represented by original concept maps and bibliographic pointers rather than copied books.'),
]

STOP = set('the a an and or but if then than to of in on for from with without into onto by as at is are was were be been being this that these those it its their they them you your we our i me my who what when where why how can could would should may might will do does did have has had not no all any some more most very just also such through between over under about after before during'.split())

def get_text(book_id):
    urls=[
      f'https://www.gutenberg.org/cache/epub/{book_id}/pg{book_id}.txt',
      f'https://www.gutenberg.org/files/{book_id}/{book_id}-8.txt',
      f'https://www.gutenberg.org/files/{book_id}/{book_id}.txt',
      f'https://www.gutenberg.org/ebooks/{book_id}.txt.utf-8',
    ]
    for u in urls:
        try:
            req=urllib.request.Request(u,headers={'User-Agent':'EchoCore-CivilizationVault/14.0 (+Android offline corpus build)'})
            with urllib.request.urlopen(req,timeout=30) as r:
                data=r.read(15_000_000)
            text=data.decode('utf-8','replace').replace('\r\n','\n').replace('\r','\n')
            if len(text)>5000:return text,u
        except Exception as e:
            last=e
    print('WARN download failed',book_id,last)
    return '',f'https://www.gutenberg.org/ebooks/{book_id}'

def clean(text):
    text=re.sub(r'\ufeff','',text)
    text=re.sub(r'[ \t]+',' ',text)
    text=re.sub(r'\n{4,}','\n\n\n',text)
    return text.strip()

def chunks(text,target=4200,overlap=280):
    text=clean(text)
    if not text:return []
    out=[]; i=0; n=len(text)
    while i<n:
        end=min(n,i+target)
        if end<n:
            cut=max(text.rfind('\n\n',i+1800,end),text.rfind('. ',i+1800,end))
            if cut>i+1800:end=cut+2
        s=text[i:end].strip()
        if len(s)>120:out.append(s)
        if end>=n:break
        i=max(i+1,end-overlap)
    return out

def words(s):return [w for w in re.findall(r"[a-z0-9][a-z0-9'-]{2,}",s.lower()) if w not in STOP and len(w)>2]

def top_terms(text,title,domain,residents):
    c=collections.Counter(words(text))
    for w in words(title+' '+domain+' '+residents):c[w]+=7
    return c.most_common(34)

con=sqlite3.connect(OUT)
cur=con.cursor()
cur.executescript('''
PRAGMA journal_mode=OFF; PRAGMA synchronous=OFF; PRAGMA temp_store=MEMORY;
CREATE TABLE meta(k TEXT PRIMARY KEY,v TEXT NOT NULL);
CREATE TABLE sources(id INTEGER PRIMARY KEY AUTOINCREMENT,source_key TEXT UNIQUE,title TEXT,author TEXT,year INTEGER,domain TEXT,residents TEXT,source_type TEXT,rights TEXT,provenance TEXT,url TEXT,char_count INTEGER DEFAULT 0,passage_count INTEGER DEFAULT 0);
CREATE TABLE passages(id INTEGER PRIMARY KEY AUTOINCREMENT,source_id INTEGER,part INTEGER,title TEXT,domain TEXT,residents TEXT,priority INTEGER,text_gz BLOB,char_count INTEGER,keywords TEXT);
CREATE TABLE terms(term TEXT,passage_id INTEGER,weight INTEGER,PRIMARY KEY(term,passage_id));
CREATE INDEX idx_terms_term ON terms(term);
CREATE INDEX idx_passages_source ON passages(source_id,part);
CREATE INDEX idx_passages_domain ON passages(domain);
CREATE INDEX idx_sources_domain ON sources(domain);
''')
cur.executemany('INSERT INTO meta(k,v) VALUES(?,?)',[('schema','civilization-vault-v1'),('app_target','14.0.0'),('built_at',str(int(time.time()))),('privacy','public-domain full text + original derived maps; no raw private user files')])

def add_source(key,title,author,year,domain,residents,stype,rights,prov,url,text,priority=8):
    ps=chunks(text) if len(text)>5000 else ([text.strip()] if text.strip() else [])
    cur.execute('INSERT INTO sources(source_key,title,author,year,domain,residents,source_type,rights,provenance,url,char_count,passage_count) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)',(key,title,author,year,domain,residents,stype,rights,prov,url,len(text),len(ps)))
    sid=cur.lastrowid
    for part,p in enumerate(ps,1):
        tt=top_terms(p,title,domain,residents); kws=' '.join(x for x,_ in tt)
        blob=gzip.compress(p.encode('utf-8'),compresslevel=9)
        cur.execute('INSERT INTO passages(source_id,part,title,domain,residents,priority,text_gz,char_count,keywords) VALUES(?,?,?,?,?,?,?,?,?)',(sid,part,title,domain,residents,priority,blob,len(p),kws))
        pid=cur.lastrowid
        cur.executemany('INSERT OR REPLACE INTO terms(term,passage_id,weight) VALUES(?,?,?)',[(t,pid,min(50,1+w)) for t,w in tt])
    return len(ps)

total_chars=0; total_passages=0; downloaded=0
for key,title,author,year,bid,domain,residents in PD:
    txt,url=get_text(bid)
    if txt:
        downloaded+=1; total_chars+=len(txt)
        n=add_source(key,title,author,year,domain,residents,'PUBLIC_DOMAIN_FULLTEXT','PUBLIC_DOMAIN_US','Project Gutenberg edition',url,txt,9)
        total_passages+=n
    else:
        capsule=f'{title} by {author}. Public-domain source registered in the Civilization Vault. Full-text mirror was unavailable during this build; source catalog URL retained for later expansion.'
        total_passages+=add_source(key,title,author,year,domain,residents,'PUBLIC_DOMAIN_INDEX','PUBLIC_DOMAIN_US','Project Gutenberg catalog',url,capsule,7)

for key,title,prov,year,domain,residents,text in CAPSULES:
    total_chars+=len(text)
    total_passages+=add_source(key,title,'Ascendant Civilization Archive',year,domain,residents,'KNOWLEDGE_MAP','DERIVED_ORIGINAL',prov,'',text,10)

cur.executemany('INSERT OR REPLACE INTO meta(k,v) VALUES(?,?)',[
 ('public_domain_sources_downloaded',str(downloaded)),('source_count',str(cur.execute('SELECT COUNT(*) FROM sources').fetchone()[0])),('passage_count',str(cur.execute('SELECT COUNT(*) FROM passages').fetchone()[0])),('uncompressed_chars',str(cur.execute('SELECT COALESCE(SUM(char_count),0) FROM passages').fetchone()[0]))])
con.commit(); con.execute('VACUUM'); con.close()
print('Civilization Vault:',OUT,'bytes',OUT.stat().st_size,'PD downloaded',downloaded,'/',len(PD),'passages',total_passages)
