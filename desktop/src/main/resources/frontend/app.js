/**
 * kiki's youtube mixer - Client Application
 */

const state = {
  // Main Panel (Active Queue)
  tracks: [],
  selectedIds: new Set(),
  lastSelectedId: null,
  lastClickedIndex: 0,
  searchQuery: '',
  activeView: 'liked_songs',
  blacklist: [],
  blacklistSearch: '',
  
  userCustomOrderIds: [],
  sortState: { column: 'order_index', direction: 'asc', isTemporary: false },
  isOrderLocked: false,
  isTrueShuffleActive: false,
  undoStack: [],
  
  // Right Panel (Dual Browser & Discovery)
  rightTab: 'search', // 'search', 'playlist', or 'discovery'
  searchModifier: 'all', // 'all', 'track', 'artist', 'album'
  rightTracks: [],
  rightSelectedIds: new Set(),
  rightLastSelectedId: null,
  rightLastClickedIndex: 0,
  allPlaylists: [],
  
  // "🎲 Surprise Me!" Discovery Matrix State
  discovery: {
    artists: [], // [{ value: 'Daft Punk', id: '...', modifier: 'AND' }]
    genres: [],  // [{ value: 'indie', modifier: 'AND' }]
    decades: [], // [{ value: '90s', modifier: 'AND' }]
    tracks: [],  // [{ value: 'Get Lucky', id: '...', modifier: 'AND' }]
    keywords: [],
    useActiveVibe: false,
    notLikedSongs: true,
    notInPlaylists: true,
    notRecentlyPlayedDays: 30,
    notLive: false,
    onlyLive: false,
    notRemix: false,
    onlyRemix: false,
    ignoreBlacklist: false,
    lowPopularityOnly: false,
    hiddenGemTarget: 'artist',
    targetCount: 30,
    trueShuffle: true,
    avoidConsecutiveArtists: true,
    artistMod: 'AND',
    genreMod: 'AND',
    decadeMod: 'AND',
    trackMod: 'AND',
    selectedGenreCategory: 'Popular',
    discoveredTracks: [],
    isGenerating: false
  },
  
  // Player state
  playerState: null,
  currentPlayingTrackId: null,
  currentPlayingTrackTitle: null,
  autoScrollLocked: true,
  isMac: false,
  authenticated: false,
  isSyncing: false,
  hasSyncedTracks: false,
  likedTrackIds: new Set(),
  currentLang: localStorage.getItem('kiki_ytm_lang') || 'es'
};

const DOM = {
  langToggleGroup: document.getElementById('lang-toggle-group'),
  langBtnEn: document.getElementById('lang-btn-en'),
  langBtnEs: document.getElementById('lang-btn-es'),
  searchInput: document.getElementById('search-input'),
  clearSearchBtn: document.getElementById('clear-search-btn'),
  topSyncBtn: document.getElementById('top-sync-btn'),
  sidebarSyncBtn: document.getElementById('sidebar-sync-btn'),
  loginBtn: document.getElementById('login-btn'),
  modal1ClickLoginBtn: document.getElementById('modal-1click-login-btn'),
  settingsBtn: document.getElementById('settings-btn'),
  connectionBadge: document.getElementById('connection-status-badge'),
  statusText: document.getElementById('status-text'),
  
  // Sidebar & Resizers
  sidebar: document.getElementById('sidebar'),
  sidebarResizer: document.getElementById('sidebar-resizer'),
  mainContent: document.getElementById('main-content'),
  rightPanelResizer: document.getElementById('right-panel-resizer'),
  rightPanel: document.getElementById('right-panel'),
  
  navItems: document.querySelectorAll('.nav-item'),
  playlistsList: document.getElementById('playlists-list'),
  remoteUrlDisplay: document.getElementById('remote-url-display'),
  
  // Main Table & Toolbar
  tracksTbody: document.getElementById('tracks-tbody'),
  tracksTable: document.getElementById('tracks-table'),
  selectAllCheckbox: document.getElementById('select-all-checkbox'),
  trackCountBadge: document.getElementById('track-count-badge'),
  playActiveListBtn: document.getElementById('play-active-list-btn'),
  shuffleActiveListBtn: document.getElementById('shuffle-active-list-btn'),
  locatePlayingBtn: document.getElementById('locate-playing-btn'),
  saveAsPlaylistBtn: document.getElementById('save-as-playlist-btn'),
  bakeShuffleBtn: document.getElementById('bake-shuffle-btn'),
  resetOrderBtn: document.getElementById('reset-order-btn'),
  saveOrderBtn: document.getElementById('save-order-btn'),
  lockOrderBtn: document.getElementById('lock-order-btn'),
  reloadViewBtn: document.getElementById('reload-view-btn'),
  clearQueueBtn: document.getElementById('clear-queue-btn'),
  emptyState: document.getElementById('empty-state'),
  connectWelcomeHero: document.getElementById('connect-welcome-hero'),
  heroLoginBtn: document.getElementById('hero-login-btn'),
  syncLoadingHero: document.getElementById('sync-loading-hero'),
  syncStatusTitle: document.getElementById('sync-status-title'),
  syncStatusDesc: document.getElementById('sync-status-desc'),
  syncProgressFill: document.getElementById('sync-progress-fill'),
  syncStageBadge: document.getElementById('sync-stage-badge'),
  syncStageText: document.getElementById('sync-stage-text'),
  logoutBtn: document.getElementById('logout-btn'),
  tableContainer: document.getElementById('table-container'),
  queueActionsRow: document.querySelector('#table-toolbar .toolbar-actions-row:not(#blacklist-toolbar-row)'),
  blacklistToolbarRow: document.getElementById('blacklist-toolbar-row'),
  blacklistAddInput: document.getElementById('blacklist-add-input'),
  blacklistSuggestions: document.getElementById('blacklist-artist-suggestions'),
  blacklistAddBtn: document.getElementById('blacklist-add-btn'),
  blacklistSearchFilter: document.getElementById('blacklist-search-filter'),
  blacklistContainer: document.getElementById('blacklist-container'),
  blacklistTableBody: document.getElementById('blacklist-table-body'),
  blacklistEmptyState: document.getElementById('blacklist-empty-state'),
  
  // Floating Batch Action Bar
  selectionActionBar: document.getElementById('selection-action-bar'),
  selectedCountText: document.getElementById('selected-count-text'),
  batchCreatePlaylistBtn: document.getElementById('batch-create-playlist-btn'),
  batchShuffleBtn: document.getElementById('batch-shuffle-btn'),
  clearSelectionBtn: document.getElementById('clear-selection-btn'),
  
  // Right Panel: Dual Source Browser & YouTube Music Search & Surprise Me
  tabRightSearch: document.getElementById('tab-right-search'),
  tabRightPlaylist: document.getElementById('tab-right-playlist'),
  tabRightDiscovery: document.getElementById('tab-right-discovery'),
  rightSearchControls: document.getElementById('right-search-controls'),
  rightPlaylistControls: document.getElementById('right-playlist-controls'),
  rightDiscoveryControls: document.getElementById('right-discovery-controls'),
  rightSearchInput: document.getElementById('right-search-input'),
  rightSearchSuggestions: document.getElementById('right-search-suggestions'),
  rightSearchSubmitBtn: document.getElementById('right-search-submit-btn'),
  searchModPills: document.querySelectorAll('.search-mod-pill'),
  rightPlaylistPicker: document.getElementById('right-playlist-picker'),
  rightStandardActionHeader: document.getElementById('right-standard-action-header'),
  rightSelectedText: document.getElementById('right-selected-text'),
  rightAddAllBtn: document.getElementById('right-add-all-btn'),
  rightAddSelectedBtn: document.getElementById('right-add-selected-btn'),
  rightItemsContainer: document.getElementById('right-items-container'),
  
  // Discovery Studio DOM
  discoveryPanelHeading: document.getElementById('discovery-panel-heading'),
  discoveryHelpBtn: document.getElementById('discovery-help-btn'),
  discDropArtist: document.getElementById('disc-drop-artist'),
  discArtistModBtn: document.getElementById('disc-artist-mod-btn'),
  discoveryArtistInput: document.getElementById('discovery-artist-input'),
  discoveryArtistSuggestions: document.getElementById('discovery-artist-suggestions'),
  discoveryAddArtistBtn: document.getElementById('discovery-add-artist-btn'),
  discoveryArtistChips: document.getElementById('discovery-artist-chips'),
  
  discGenreModBtn: document.getElementById('disc-genre-mod-btn'),
  discoveryGenreInput: document.getElementById('discovery-genre-input'),
  discoveryGenreSuggestions: document.getElementById('discovery-genre-suggestions'),
  discoveryAddGenreBtn: document.getElementById('discovery-add-genre-btn'),
  discoveryGenrePills: document.getElementById('discovery-genre-pills'),
  genreCatPills: document.querySelectorAll('.genre-cat-pill'),
  discoveryGenreChips: document.getElementById('discovery-genre-chips'),
  
  discDropDecade: document.getElementById('disc-drop-decade'),
  discDecadeModBtn: document.getElementById('disc-decade-mod-btn'),
  discoveryDecadePills: document.getElementById('discovery-decade-pills'),
  
  discDropTrack: document.getElementById('disc-drop-track'),
  discTrackModBtn: document.getElementById('disc-track-mod-btn'),
  discoveryTrackInput: document.getElementById('discovery-track-input'),
  discoveryTrackSuggestions: document.getElementById('discovery-track-suggestions'),
  discoveryTrackChips: document.getElementById('discovery-track-chips'),
  discoveryActiveVibeBtn: document.getElementById('discovery-active-vibe-btn'),
  
  discNotLiked: document.getElementById('disc-not-liked'),
  discNotPlaylists: document.getElementById('disc-not-playlists'),
  discIgnoreBlacklist: document.getElementById('disc-ignore-blacklist'),
  discRecentDaysRadios: document.querySelectorAll('input[name="disc-recent-days"]'),
  discLiveModeRadios: document.querySelectorAll('input[name="disc-live-mode"]'),
  discRemixModeRadios: document.querySelectorAll('input[name="disc-remix-mode"]'),
  discLowPopularity: document.getElementById('disc-low-popularity'),
  discLowPopLabel: document.getElementById('disc-low-pop-label'),
  hiddenGemTargetWrap: document.getElementById('hidden-gem-target-wrap'),
  discHiddenGemTargetRadios: document.querySelectorAll('input[name="disc-hidden-gem-target"]'),
  
  discTargetCountRadios: document.querySelectorAll('input[name="disc-target-count"]'),
  discTrueShuffle: document.getElementById('disc-true-shuffle'),
  discoveryGenerateBtn: document.getElementById('discovery-generate-btn'),
  
  discoveryResultsSection: document.getElementById('discovery-results-section'),
  discoveryItemsContainer: document.getElementById('discovery-items-container'),
  discoveryResultCountBadge: document.getElementById('discovery-result-count-badge'),
  discPlayDirectBtn: document.getElementById('disc-play-direct-btn'),
  discSelectAllBtn: document.getElementById('disc-select-all-btn'),
  discReplaceQueueBtn: document.getElementById('disc-replace-queue-btn'),
  discReplacePlayBtn: document.getElementById('disc-replace-play-btn'),
  discAppendQueueBtn: document.getElementById('disc-append-queue-btn'),
  discAppendSelectedBtn: document.getElementById('disc-append-selected-btn'),
  
  // Bottom Player Bar
  playerTitle: document.getElementById('player-title'),
  playerArtist: document.getElementById('player-artist'),
  playerTrackInfo: document.getElementById('player-track-info'),
  playerLocateBtn: document.getElementById('player-locate-btn'),
  ctrlPrev: document.getElementById('ctrl-prev'),
  ctrlPlaypause: document.getElementById('ctrl-playpause'),
  ctrlNext: document.getElementById('ctrl-next'),
  ctrlTrueShuffle: document.getElementById('ctrl-true-shuffle'),
  progressBarWrap: document.getElementById('progress-bar-wrap'),
  progressBarFill: document.getElementById('progress-bar-fill'),
  volumeSlider: document.getElementById('volume-slider'),
  deviceSelect: document.getElementById('device-select'),
  
  // Modals
  createPlaylistModal: document.getElementById('create-playlist-modal'),
  closeCreatePlaylistModal: document.getElementById('close-create-playlist-modal'),
  cancelCreatePlaylistModal: document.getElementById('cancel-create-playlist-modal'),
  confirmCreatePlaylistModal: document.getElementById('confirm-create-playlist-modal'),
  newPlaylistName: document.getElementById('new-playlist-name'),
  newPlaylistDesc: document.getElementById('new-playlist-desc'),
  createPlaylistCountHint: document.getElementById('create-playlist-count-hint'),
  
  settingsModal: document.getElementById('settings-modal'),
  closeSettingsModal: document.getElementById('close-settings-modal'),
  closeSettingsBtnBottom: document.getElementById('close-settings-btn-bottom'),
  settingsCookieInput: document.getElementById('settings-cookie-input'),
  saveCookieBtn: document.getElementById('save-cookie-btn'),
  saveCredentialsBtn: document.getElementById('save-credentials-btn'),
  settingsClientId: document.getElementById('settings-client-id'),
  settingsClientSecret: document.getElementById('settings-client-secret'),
  
  // Sync Conflict Modal
  syncConflictModal: document.getElementById('sync-conflict-modal'),
  closeSyncConflictModal: document.getElementById('close-sync-conflict-modal'),
  cancelSyncConflictModal: document.getElementById('cancel-sync-conflict-modal'),
  confirmSyncConflictModal: document.getElementById('confirm-sync-conflict-modal'),
  syncConflictDesc: document.getElementById('sync-conflict-desc'),
  
  // Confirmation / Rename Dialog Modal
  confirmModal: document.getElementById('confirm-modal'),
  closeConfirmModal: document.getElementById('close-confirm-modal'),
  confirmModalTitle: document.getElementById('confirm-modal-title'),
  confirmModalMessage: document.getElementById('confirm-modal-message'),
  confirmModalInputGroup: document.getElementById('confirm-modal-input-group'),
  confirmModalInputLabel: document.getElementById('confirm-modal-input-label'),
  confirmModalInput: document.getElementById('confirm-modal-input'),
  confirmModalCancelBtn: document.getElementById('confirm-modal-cancel-btn'),
  confirmModalSubmitBtn: document.getElementById('confirm-modal-submit-btn'),

  // Backups Modal
  topBackupsBtn: document.getElementById('top-backups-btn'),
  backupsModal: document.getElementById('backups-modal'),
  closeBackupsModal: document.getElementById('close-backups-modal'),
  closeBackupsBtnBottom: document.getElementById('close-backups-btn-bottom'),
  createManualBackupBtn: document.getElementById('create-manual-backup-btn'),
  backupsListContainer: document.getElementById('backups-list-container'),
  
  // Context Menus
  customContextMenu: document.getElementById('custom-context-menu'),
  ctxToggleLike: document.getElementById('ctx-toggle-like'),
  playlistContextMenu: document.getElementById('playlist-context-menu'),
  ctxPlaylistSync: document.getElementById('ctx-playlist-sync'),
  toastContainer: document.getElementById('toast-container')
};

let activeContextMenuPlaylist = null;
let activeSyncPlaylist = null;
let confirmModalCallback = null;

// --- Internationalization (i18n) System: English & Spanish ---
const I18N = {
  en: {
    appSubBadge: 'Queue & Mix Studio',
    filterPlaceholder: 'Filter active list... (Keyboard shortcut: /)',
    clearFilter: 'Clear Filter',
    loginBtn: 'Log in with YouTube Music',
    loginNeeded: 'Not Logged',
    syncBtn: 'Sync Library',
    syncingBtn: 'Syncing...',
    syncHeroTitle: 'Syncing your YouTube Music Library...',
    syncHeroDesc: 'Connecting to YouTube Music and downloading your Liked Songs and playlists. Please wait a moment...',
    syncStageFetchingLiked: 'Fetching Liked Songs from YouTube Music...',
    syncStageSavingLiked: 'Saving Liked Songs to library...',
    syncStageFetchingPlaylists: 'Fetching your YouTube Music playlists...',
    syncStageDone: '✅ Library synced successfully!',
    connectHeroTitle: "Welcome to kiki's youtube mixer",
    connectHeroDesc: 'Connect your YouTube Music account with 1 click to load your liked songs, browse your playlists, and use Shuffle.',
    connectHeroBtn: '🟢 1-Click Connect with YouTube Music',
    connectPromptPlaylists: 'Connect YouTube Music to load your playlists.',
    settingsBtn: 'Settings & Setup',
    checking: 'Checking...',
    connected: 'Connected',
    offline: 'Offline',
    
    // Sidebar
    libraryTitle: 'LIBRARY',
    likedSongs: 'Liked Songs',
    allTracks: 'All Tracks',
    yourPlaylists: 'YOUR PLAYLISTS',
    androidAccess: '📱 Android Phone Access',
    androidCardDesc: 'Open this URL on your phone browser on the same Wi-Fi to control playback remotely.',
    
    // Main Toolbar & Table
    activeQueue: 'Active Listening Queue',
    queueInfoBadge: 'ℹ️ Info',
    queueSubtextDefault: 'These are the songs you will hear when you hit play.',
    queueSubtextWithCount: 'These {count} tracks will play in order when you click Play.',
    playListBtn: '▶ Play List',
    trueShuffleBtn: '🔀 Shuffle',
    saveAsPlaylistBtn: '💾 Save as Playlist',
    unlockedBtn: '🔓 Unlocked',
    lockedBtn: '🔒 Locked',
    resetOrderBtn: '🔄 Reset to User Order',
    saveOrderBtn: '💾 Save as User Order',
    
    // Floating Bar
    selectedCount: '🟢 {count} track{s} selected',
    makePlaylistBtn: '➕ Make YouTube Music Playlist',
    shuffleSelectedBtn: '🔀 Shuffle Selected',
    clearEsc: 'Clear (Esc)',
    
    // Headers
    colNumber: '#',
    colTitle: 'Title',
    colArtist: 'Artist',
    colAlbum: 'Album',
    colDuration: '⏱️',
    
    // Right Panel
    tabSearch: '🔍 Search',
    tabBrowseList: '📑 Browse Playlists',
    tabDiscovery: '🎲 Surprise Me!',
    searchCatalogPlaceholder: 'Search YouTube Music catalog...',
    searchBtn: 'Search',
    modAll: 'All',
    modSong: '🎵 Song',
    modArtist: '🎤 Artist',
    modLyrics: '📜 Lyrics',
    selectPlaylistOption: 'Select Playlist to Browse...',
    addSelectedBtn: '➕ Add to Main List',
    rightSearchPlaceholder: 'Type above to search YouTube Music or choose a playlist to browse songs.',
    
    // Player
    notPlaying: 'Not Playing',
    openPlayerPrompt: 'Open YouTube Music on Mac or Android',
    selectDevicePrompt: '📱 Select Device',
    
    // Context Menu
    ctxPlayFromHere: '▶ Play from Here',
    ctxSaveToLiked: '💚 Save to Liked Songs',
    ctxRemoveFromLiked: '🤍 Remove from Liked Songs',
    ctxTrueShuffleSelected: '🔀 Shuffle Selected',
    ctxMakePlaylist: '➕ Make YouTube Music Playlist...',
    ctxRemoveFromList: '🗑️ Remove Selected from List',
    ctxKeepOnlySelected: '🎯 Keep Only Selected (Remove Others)',
    toastRemovedFromList: '🗑️ Removed {count} track{s} from active list (Cmd+Z to undo)',
    toastKeptOnlySelected: '🎯 Kept {kept} track{s}, removed {removed} other{s} (Cmd+Z to undo)',
    toastAddedToLiked: '💚 Saved to Liked Songs',
    toastRemovedFromLiked: '🤍 Removed from Liked Songs',
    ctxRenamePlaylist: '✏️ Rename Playlist',
    ctxDeletePlaylist: '🗑️ Delete Playlist',
    
    // Tooltips
    tipSearchInputTitle: 'Filter Tracklist',
    tipSearchInput: "Press the '/' key anytime to immediately search and filter visible tracks without clicking.",
    tipClearFilterTitle: 'Clear Filter',
    tipClearFilter: 'Reset search filter and show all tracks.',
    tipLoginTitle: 'YouTube Music Login',
    tipLogin: 'Connect your YouTube Music account to load playlists and control playback.',
    tipSyncTitle: 'Sync Library',
    tipSync: 'Fetch latest liked songs, playlists, and track metadata from YouTube Music.',
    tipSettingsTitle: 'Settings & Setup',
    tipSettings: 'YouTube Music account settings, developer credentials, and anti-clumping options.',
    tipConnectionTitle: 'Audio Deviceion',
    tipConnection: 'Displays connection state to YouTube Music API and local desktop app.',
    tipLikedSongsTitle: 'Liked Songs',
    tipLikedSongs: 'All songs saved to your Liked Songs library.',
    tipAllTracksTitle: 'All Tracks',
    tipAllTracks: 'Consolidated master list of all synced songs across your library.',
    tipResizeSidebarTitle: 'Resize Sidebar',
    tipResizeSidebar: 'Click and drag horizontally to resize sidebar width.',
    tipPlayListTitle: 'Play Active List',
    tipPlayList: 'Starts continuous playback from the first track in this active list.',
    tipShuffleTitle: 'Shuffle',
    tipShuffle: 'Generates a completely new random order every time you activate it.',
    reloadViewBtn: '🔄 Reload',
    tipReloadTitle: 'Reload Original List',
    tipReload: 'Reload the clean, complete list from your library/playlist, resetting temporary workspace changes.',
    clearQueueBtn: '🗑️ Clear',
    tipClearQueueTitle: 'Clear Workspace',
    tipClearQueue: 'Empties the active workspace list without deleting songs from your library. Undoable with Cmd+Z.',
    discPlayDirectBtn: '▶ Play Mix',
    tipDiscPlayDirectTitle: 'Play Discovery Mix',
    tipDiscPlayDirect: 'Plays all discovered tracks directly in YouTube Music without modifying your main workspace queue.',
    tipLockOrderTitle: 'Order Lock',
    tipLockOrder: 'Toggle locking to prevent accidental drag reordering or shuffling.',
    tipResetOrderTitle: 'Reset Sequence',
    tipResetOrder: 'Revert temporary column header sorting back to your saved custom order.',
    tipSaveOrderTitle: 'Save Sequence',
    tipSaveOrder: 'Save the current column-sorted list as your permanent custom order.',
    tipSaveAsPlaylistTitle: 'Save as Playlist',
    tipSaveAsPlaylistActive: 'Save this unique list of {count} songs as a new YouTube Music playlist.',
    tipSaveAsPlaylistExistsTitle: 'Playlist Already Exists',
    tipSaveAsPlaylistExists: 'This exact list and song order matches "{name}".',
    tipSaveAsPlaylistEmpty: 'The active list has no songs to save.',
    tipQueueInfoTitle: 'Active Listening Queue',
    tipQueueInfo: 'When you click ▶ Play List or press Space, YouTube Music plays these songs in the order they are shown below.',
    tipSelectAllTitle: 'Select All',
    tipSelectAll: 'Toggle selection of all visible tracks in the list.',
    tipSortNumTitle: 'Sort by Number',
    tipSortNum: 'Sort tracks by custom sequence order.',
    tipSortTitleTitle: 'Sort by Title',
    tipSortTitle: 'Sort tracks alphabetically by song title.',
    tipSortArtistTitle: 'Sort by Artist',
    tipSortArtist: 'Sort tracks alphabetically by primary artist name.',
    tipSortAlbumTitle: 'Sort by Album',
    tipSortAlbum: 'Sort tracks alphabetically by album name.',
    tipSortDurationTitle: 'Sort by Duration',
    tipSortDuration: 'Sort tracks from shortest to longest duration.',
    tipCreatePlaylistTitle: 'Create YouTube Music Playlist',
    tipCreatePlaylist: 'Export all currently selected tracks to a brand new YouTube Music playlist.',
    tipShuffleSelectedTitle: 'Shuffle Selection',
    tipShuffleSelected: 'This is a real random shuffle. It randomizes the selected songs every time you activate it.',
    tipDeselectAllTitle: 'Deselect All',
    tipDeselectAll: 'Clear track selection.',
    tipResizeRightTitle: 'Resize Right Panel',
    tipResizeRight: 'Click and drag horizontally to resize right panel width.',
    tipSearchTabTitle: 'YouTube Music Search',
    tipSearchTab: "Search and preview millions of tracks from YouTube Music's global catalog.",
    tipBrowseTabTitle: 'Browse Playlists',
    tipBrowseTab: 'Browse tracks from your other YouTube Music playlists to drag into your queue.',
    tipSearchCatalogTitle: 'Search Catalog',
    tipSearchCatalog: 'Enter song titles, artist names, or lyrics to search YouTube Music.',
    tipRunSearchTitle: 'Run Search',
    tipRunSearch: 'Query YouTube Music for matching tracks.',
    tipModAllTitle: 'All Fields',
    tipModAll: 'Search across track titles, artists, and albums simultaneously.',
    tipModSongTitle: 'Song Titles Only',
    tipModSong: 'Filter query results strictly by song title.',
    tipModArtistTitle: 'Artists Only',
    tipModArtist: 'Filter query results strictly by artist name.',
    tipModLyricsTitle: 'Lyric Search',
    tipModLyrics: 'Search YouTube Music catalog by lyrics and spoken phrases.',
    tipSelectPlaylistTitle: 'Select Playlist',
    tipSelectPlaylist: 'Choose one of your YouTube Music playlists to view and drag tracks from.',
    tipAddSelectedTitle: 'Add to Main List',
    tipAddSelected: 'Append selected songs from the right panel directly into your active list.',
    tipNowPlayingTitle: 'Now Playing',
    tipNowPlaying: 'Shows the currently playing track and artist on YouTube Music.',
    tipPrevTitle: 'Previous Track',
    tipPrev: 'Play the previous song in queue.',
    tipPlayPauseTitle: 'Play / Pause',
    tipPlayPause: 'Toggle playback on YouTube Music.',
    tipNextTitle: 'Next Track',
    tipNext: 'Skip to the next song in queue.',
    tipVolumeTitle: 'Volume',
    tipVolume: 'Adjust YouTube Music streaming volume level.',
    tipDeviceTitle: 'Playback Device',
    tipDevice: 'Select active Audio Device output device (Mac, Phone, Speaker).',
    tipPlayTrackTitle: 'Play Track',
    tipPlayTrack: 'Play this track directly in YouTube Music and queue remaining songs.',
    tipPauseTrackTitle: 'Pause Track',
    tipPauseTrack: 'Pause current playback in YouTube Music.',
    tipResumeTrackTitle: 'Resume Track',
    tipResumeTrack: 'Resume playing this track in YouTube Music.',
    
    // Discovery Engine
    discoveryPanelHeading: 'Discovery Engine',
    discoveryHelpTitle: 'Discovery Engine Guide',
    discoveryHelp: 'Combine artists, genres, decades and tracks with [+ AND] (include) and [- NOT] (exclude) modifiers. Strict filters guarantee you will not hear songs already in your Liked Songs or Playlists.',
    artistModLabel: '🎤 Artists (AND / NOT):',
    artistPlaceholder: 'Type artist name (e.g. Daft Punk)...',
    genreModLabel: '🎸 Genres (AND / NOT):',
    genrePlaceholder: 'Search genres (e.g. indie, synth-pop)...',
    genreCatHint: '📂 Category Tabs (switches genre pills below):',
    decadeModLabel: '📅 Decades (AND / NOT):',
    songSeedModLabel: '🎵 Song Seed & Queue Vibe:',
    songSeedPlaceholder: 'Type song title (e.g. Get Lucky)...',
    blendQueueVibeBtn: '🔮 Blend Active Queue Vibe',
    strictExclusionsTitle: '🚫 STRICT EXCLUSION FILTERS',
    notLikedSongsLabel: 'NOT in Liked Songs (Genuinely New)',
    notInPlaylistsLabel: 'NOT in Any of My Playlists',
    notRecentlyPlayedLabel: 'NOT Recently Played:',
    notRecentlyPlayedTitle: '🕒 NOT Recently Played:',
    notRecentOff: 'Off',
    notRecent7d: 'Past 7 Days',
    notRecent30d: 'Past 30 Days',
    discLiveModeTitle: '🎤 Live Versions:',
    discLiveNotLabel: '🚫 NOT Live',
    discLiveAnyLabel: 'All',
    discLiveOnlyLabel: '🎤 ONLY Live',
    discRemixModeTitle: '🎛️ Remix & Remaster:',
    discRemixNotLabel: '🚫 NOT Remix',
    discRemixAnyLabel: 'All',
    discRemixOnlyLabel: '🎛️ ONLY Remix',
    lowPopularityLabel: '💎 Low Popularity Only (Hidden Gems)',
    targetTracksLabel: 'Target Tracks:',
    trueShuffleAntiClumpLabel: '🔀 Shuffle + Anti-Clumping',
    generateDiscoveryBtn: 'Generate Discovery Mix',
    generatingDiscoveryBtn: 'Generating Mix...',
    replaceMainQueueBtn: '🔄 Replace Main Queue',
    replacePlayBtn: '▶ Replace & Play Now',
    appendMainQueueBtn: '➕ Append to Main Queue',
    appendSelectedBtn: '➕ Append Selected',
    discoveredCountBadge: '{count} discovered',
    discoveryToastSuccess: '🎲 Generated {count} fresh discovery tracks!',
    discoveryToastReplaced: '🔄 Replaced active listening queue with {count} discovery tracks',
    discoveryToastAppended: '➕ Added {count} discovery tracks to the bottom of the active queue',
    tipDiscoveryTabTitle: 'Surprise Me!',
    tipDiscoveryTab: 'Generate a smart discovery mix with multi-criteria AND/NOT seeds, strict negative exclusion filters, and shuffle.',
    tipArtistModTitle: 'Artist Modifier',
    tipArtistMod: 'Toggle between [+ AND] (include artists) and [- NOT] (exclude artists).',
    tipGenreModTitle: 'Genre Modifier',
    tipGenreMod: 'Toggle between [+ AND] (include genre) and [- NOT] (exclude genre).',
    tipTrackModTitle: 'Song Seed Modifier',
    tipTrackMod: 'Toggle between [+ AND] (similar to song) and [- NOT] (dissimilar).',
    tipDecadeModTitle: 'Decade Modifier',
    tipDecadeMod: 'Toggle between [+ AND] (include decade) and [- NOT] (exclude decade).',
    tipTriStateGenreTitle: '3-State Genre Pills',
    tipTriStateGenre: 'Click once for [+ AND] (Include), click again for [- NOT] (Exclude), click third time for Off.',
    tipTriStateDecadeTitle: '3-State Decade Pills',
    tipTriStateDecade: 'Click once for [+ AND] (Include), click again for [- NOT] (Exclude), click third time for Off.',
    tipActiveVibeTitle: 'Active Queue Vibe',
    tipActiveVibe: 'Analyzes the top artists and genres from your currently loaded main listening queue and blends them into this discovery mix.',
    tipNotLikedTitle: 'NOT in Liked Songs',
    tipNotLiked: 'Guarantees 100% brand new music by excluding every song in your Liked Songs library.',
    tipNotInPlaylistsTitle: 'NOT in Any Playlist',
    tipNotInPlaylists: 'Excludes any song that is already saved in any of your YouTube Music playlists.',
    discIgnoreBlacklistLabel: '🔓 Unlock Blacklist for this mix',
    tipIgnoreBlacklistTitle: 'Unlock Blacklist',
    tipIgnoreBlacklist: 'Momentarily permit blacklisted artists in this mix without removing them from your permanent blacklist.',
    tipNotRecentTitle: 'NOT Recently Played',
    tipNotRecent: 'Excludes songs played within the last 7 or 30 days.',
    tipLiveNotTitle: 'NOT Live',
    tipLiveNot: 'Filters out concert recordings, live albums, and en vivo tracks.',
    tipLiveOnlyTitle: 'ONLY Live',
    tipLiveOnly: 'Searches and filters exclusively for live performances, concert recordings, and unplugged versions.',
    tipRemixNotTitle: 'NOT Remix',
    tipRemixNot: 'Filters out club edits, remixes, VIP mixes, and remastered tracks.',
    tipRemixOnlyTitle: 'ONLY Remix / Remastered',
    tipRemixOnly: 'Searches and filters exclusively for remixes, extended club mixes, VIP edits, and remastered tracks.',
    tipReplaceQueueTitle: 'Replace Main Queue',
    tipReplaceQueue: 'Clears all songs currently in your active listening queue and loads all newly discovered tracks.',
    tipReplacePlayTitle: 'Replace & Play Immediately',
    tipReplacePlay: 'Clears current main queue, loads discovered tracks, and starts playback instantly with Shuffle.',
    tipAppendQueueTitle: 'Append to Main Queue',
    tipAppendQueue: 'Adds all discovered tracks to the bottom of the main queue without removing existing songs.',
    tipAppendSelectedTitle: 'Append Selected Tracks',
    tipAppendSelected: 'Adds only the checked tracks to the bottom of the main queue.'
  },
  es: {
    appSubBadge: 'Estudio de Mezcla y Cola',
    filterPlaceholder: 'Filtrar lista activa... (Atajo de teclado: /)',
    clearFilter: 'Borrar filtro',
    loginBtn: 'Iniciar sesión con YouTube Music',
    loginNeeded: 'No conectado',
    syncBtn: 'Sincronizar biblioteca',
    syncingBtn: 'Sincronizando...',
    syncHeroTitle: 'Sincronizando tu biblioteca de YouTube Music...',
    syncHeroDesc: 'Conectando a YouTube Music y descargando tus canciones guardadas y playlists. Por favor espera un momento...',
    syncStageFetchingLiked: 'Obteniendo canciones guardadas de YouTube Music...',
    syncStageSavingLiked: 'Guardando canciones en la biblioteca local...',
    syncStageFetchingPlaylists: 'Obteniendo tus playlists de YouTube Music...',
    syncStageDone: '✅ ¡Biblioteca sincronizada con éxito!',
    connectHeroTitle: "Bienvenido a kiki's youtube mixer",
    connectHeroDesc: 'Conecta tu cuenta de YouTube Music con 1 clic para cargar tus canciones guardadas, explorar tus playlists y usar el modo Aleatorio.',
    connectHeroBtn: '🟢 Conectar con YouTube Music en 1 clic',
    connectPromptPlaylists: 'Conecta YouTube Music para cargar tus playlists.',
    settingsBtn: 'Configuración',
    checking: 'Comprobando...',
    connected: 'Conectado',
    offline: 'Desconectado',
    
    // Sidebar
    libraryTitle: 'BIBLIOTECA',
    likedSongs: 'Canciones que te gustan',
    allTracks: 'Todas las canciones',
    yourPlaylists: 'TUS PLAYLISTS',
    androidAccess: '📱 Acceso desde Android',
    androidCardDesc: 'Abre esta URL en el navegador de tu teléfono en la misma red Wi-Fi para control remoto.',
    
    // Main Toolbar & Table
    activeQueue: 'Cola de reproducción activa',
    queueInfoBadge: 'ℹ️ Info',
    queueSubtextDefault: 'Estas son las canciones que escucharás al darle a Reproducir.',
    queueSubtextWithCount: 'Estas {count} canciones se reproducirán en orden al hacer clic en Reproducir.',
    playListBtn: '▶ Reproducir lista',
    trueShuffleBtn: '🔀 Aleatorio',
    saveAsPlaylistBtn: '💾 Guardar como lista',
    unlockedBtn: '🔓 Desbloqueado',
    lockedBtn: '🔒 Bloqueado',
    resetOrderBtn: '🔄 Restaurar orden',
    saveOrderBtn: '💾 Guardar orden',
    
    // Floating Bar
    selectedCount: '🟢 {count} canción{es} seleccionada{s}',
    makePlaylistBtn: '➕ Crear playlist en YouTube Music',
    shuffleSelectedBtn: '🔀 Mezclar selección',
    clearEsc: 'Deseleccionar (Esc)',
    
    // Headers
    colNumber: '#',
    colTitle: 'Título',
    colArtist: 'Artista',
    colAlbum: 'Álbum',
    colDuration: '⏱️',
    
    // Right Panel
    tabSearch: '🔍 Buscar',
    tabBrowseList: '📑 Playlists',
    tabDiscovery: '🎲 ¡Sorpréndeme!',
    searchCatalogPlaceholder: 'Buscar en el catálogo de YouTube Music...',
    searchBtn: 'Buscar',
    modAll: 'Todos',
    modSong: '🎵 Canción',
    modArtist: '🎤 Artista',
    modLyrics: '📜 Letras',
    selectPlaylistOption: 'Selecciona una playlist para explorar...',
    addSelectedBtn: '➕ Añadir a lista principal',
    rightSearchPlaceholder: 'Escribe arriba para buscar en YouTube Music o elige una playlist para explorar.',
    
    // Player
    notPlaying: 'Sin reproducción activa',
    openPlayerPrompt: 'Abre YouTube Music en tu Mac o teléfono Android',
    selectDevicePrompt: '📱 Seleccionar dispositivo',
    
    // Context Menu
    ctxPlayFromHere: '▶ Reproducir desde aquí',
    ctxSaveToLiked: '💚 Guardar en Canciones que te gustan',
    ctxRemoveFromLiked: '🤍 Quitar de Canciones que te gustan',
    ctxTrueShuffleSelected: '🔀 Aleatorio de seleccionadas',
    ctxMakePlaylist: '➕ Crear playlist en YouTube Music...',
    ctxRemoveFromList: '🗑️ Quitar seleccionadas de la lista',
    ctxKeepOnlySelected: '🎯 Mantener solo seleccionadas (Quitar las demás)',
    toastRemovedFromList: '🗑️ Se quitaron {count} canción{s} de la lista activa (Cmd+Z para deshacer)',
    toastKeptOnlySelected: '🎯 Se mantuvieron {kept} canción{es}, se quitaron {removed} restante{s} (Cmd+Z para deshacer)',
    toastAddedToLiked: '💚 Guardada en Canciones que te gustan',
    toastRemovedFromLiked: '🤍 Quitada de Canciones que te gustan',
    ctxRenamePlaylist: '✏️ Renombrar playlist',
    ctxDeletePlaylist: '🗑️ Eliminar playlist',
    
    // Tooltips
    tipSearchInputTitle: 'Filtrar lista',
    tipSearchInput: "Pulsa la tecla '/' en cualquier momento para buscar y filtrar canciones sin necesidad del ratón.",
    tipClearFilterTitle: 'Borrar filtro',
    tipClearFilter: 'Restablecer filtro y mostrar todas las canciones.',
    tipLoginTitle: 'Iniciar sesión',
    tipLogin: 'Conecta tu cuenta de YouTube Music para cargar tus playlists y controlar la música.',
    tipSyncTitle: 'Sincronizar biblioteca',
    tipSync: 'Obtener las últimas canciones guardadas, playlists y metadatos de YouTube Music.',
    tipSettingsTitle: 'Configuración',
    tipSettings: 'Ajustes de cuenta, claves de desarrollador y opciones de reproducción.',
    tipConnectionTitle: 'Conexión con YouTube Music',
    tipConnection: 'Muestra el estado de conexión con la API de YouTube Music y la app de escritorio.',
    tipLikedSongsTitle: 'Canciones que te gustan',
    tipLikedSongs: 'Todas las canciones guardadas en tu biblioteca de Canciones que te gustan.',
    tipAllTracksTitle: 'Todas las canciones',
    tipAllTracks: 'Lista maestra consolidada de todas las canciones sincronizadas.',
    tipResizeSidebarTitle: 'Redimensionar barra lateral',
    tipResizeSidebar: 'Haz clic y arrastra horizontalmente para cambiar el ancho de la barra lateral.',
    tipPlayListTitle: 'Reproducir lista activa',
    tipPlayList: 'Inicia la reproducción continua desde la primera canción de esta lista activa.',
    tipShuffleTitle: 'Aleatorio',
    tipShuffle: 'Este es un modo aleatorio. Genera un orden aleatorio totalmente nuevo cada vez que lo activas.',
    reloadViewBtn: '🔄 Recargar',
    tipReloadTitle: 'Recargar Lista Original',
    tipReload: 'Recarga la lista limpia y completa desde tu biblioteca o playlist, descartando cambios temporales.',
    clearQueueBtn: '🗑️ Limpiar',
    tipClearQueueTitle: 'Limpiar Espacio de Trabajo',
    tipClearQueue: 'Vacía la lista activa de trabajo sin borrar canciones de tu biblioteca. Puedes deshacer con Cmd+Z.',
    discPlayDirectBtn: '▶ Reproducir Mix',
    tipDiscPlayDirectTitle: 'Reproducir Mix Descubierto',
    tipDiscPlayDirect: 'Reproduce todas las canciones descubiertas directamente en YouTube Music sin modificar tu lista activa.',
    tipLockOrderTitle: 'Bloqueo de orden',
    tipLockOrder: 'Bloquea o desbloquea el arrastre para evitar reordenamientos accidentales.',
    tipResetOrderTitle: 'Restaurar orden',
    tipResetOrder: 'Vuelve al orden personalizado guardado deshaciendo la ordenación por columnas.',
    tipSaveOrderTitle: 'Guardar orden',
    tipSaveOrder: 'Guarda el orden actual de columnas como tu orden personalizado definitivo.',
    tipSaveAsPlaylistTitle: 'Guardar como lista',
    tipSaveAsPlaylistActive: 'Guarda esta lista única de {count} canciones como una nueva playlist en YouTube Music.',
    tipSaveAsPlaylistExistsTitle: 'La playlist ya existe',
    tipSaveAsPlaylistExists: 'Esta lista y orden exacto coincide con "{name}".',
    tipSaveAsPlaylistEmpty: 'La lista activa no tiene canciones para guardar.',
    tipQueueInfoTitle: 'Cola de reproducción activa',
    tipQueueInfo: 'Al hacer clic en ▶ Reproducir lista o pulsar Espacio, YouTube Music reproduce estas canciones en el orden que se muestra abajo.',
    tipSelectAllTitle: 'Seleccionar todo',
    tipSelectAll: 'Selecciona o deselecciona todas las canciones visibles de la lista.',
    tipSortNumTitle: 'Ordenar por número',
    tipSortNum: 'Ordena las canciones según la secuencia personalizada.',
    tipSortTitleTitle: 'Ordenar por título',
    tipSortTitle: 'Ordena las canciones alfabéticamente por título.',
    tipSortArtistTitle: 'Ordenar por artista',
    tipSortArtist: 'Ordena las canciones alfabéticamente por nombre del artista principal.',
    tipSortAlbumTitle: 'Ordenar por álbum',
    tipSortAlbum: 'Ordena las canciones alfabéticamente por nombre de álbum.',
    tipSortDurationTitle: 'Ordenar por duración',
    tipSortDuration: 'Ordena las canciones de menor a mayor duración.',
    tipCreatePlaylistTitle: 'Crear playlist en YouTube Music',
    tipCreatePlaylist: 'Exporta todas las canciones seleccionadas a una nueva playlist en tu cuenta de YouTube Music.',
    tipShuffleSelectedTitle: 'Mezclar selección',
    tipShuffleSelected: 'Mezcla las canciones seleccionadas cada vez que lo activas.',
    tipDeselectAllTitle: 'Deseleccionar todo',
    tipDeselectAll: 'Borra la selección de canciones.',
    tipResizeRightTitle: 'Redimensionar panel derecho',
    tipResizeRight: 'Haz clic y arrastra horizontalmente para cambiar el ancho del panel derecho.',
    tipSearchTabTitle: 'Buscar en YouTube Music',
    tipSearchTab: 'Busca y previsualiza millones de canciones en el catálogo mundial de YouTube Music.',
    tipBrowseTabTitle: 'Explorar playlists',
    tipBrowseTab: 'Explora canciones de tus otras playlists para arrastrarlas a tu cola de reproducción.',
    tipSearchCatalogTitle: 'Buscar en el catálogo',
    tipSearchCatalog: 'Escribe títulos de canciones, artistas o letras para buscar en YouTube Music.',
    tipRunSearchTitle: 'Ejecutar búsqueda',
    tipRunSearch: 'Consulta a YouTube Music por canciones coincidentes.',
    tipModAllTitle: 'Todos los campos',
    tipModAll: 'Busca simultáneamente en títulos de canciones, artistas y álbumes.',
    tipModSongTitle: 'Solo títulos de canción',
    tipModSong: 'Filtra los resultados estrictamente por el título de la canción.',
    tipModArtistTitle: 'Solo artistas',
    tipModArtist: 'Filtra los resultados estrictamente por el nombre del artista.',
    tipModLyricsTitle: 'Búsqueda por letra',
    tipModLyrics: 'Busca canciones en el catálogo de YouTube Music por fragmentos de su letra.',
    tipSelectPlaylistTitle: 'Seleccionar playlist',
    tipSelectPlaylist: 'Elige una de tus playlists de YouTube Music para ver y arrastrar canciones.',
    tipAddSelectedTitle: 'Añadir a lista principal',
    tipAddSelected: 'Agrega las canciones seleccionadas del panel derecho directamente a tu lista activa.',
    tipNowPlayingTitle: 'En reproducción',
    tipNowPlaying: 'Muestra la canción y el artista que se están reproduciendo actualmente en YouTube Music.',
    tipPrevTitle: 'Canción anterior',
    tipPrev: 'Reproduce la canción anterior en la cola.',
    tipPlayPauseTitle: 'Reproducir / Pausar',
    tipPlayPause: 'Alterna la reproducción en YouTube Music.',
    tipNextTitle: 'Siguiente canción',
    tipNext: 'Salta a la siguiente canción en la cola.',
    tipVolumeTitle: 'Volumen',
    tipVolume: 'Ajusta el nivel de volumen de YouTube Music.',
    tipDeviceTitle: 'Dispositivo de reproducción',
    tipDevice: 'Selecciona el dispositivo activo de Audio Device (Mac, Teléfono, Altavoz).',
    tipPlayTrackTitle: 'Reproducir canción',
    tipPlayTrack: 'Reproduce esta canción directamente en YouTube Music y encola las siguientes.',
    tipPauseTrackTitle: 'Pausar canción',
    tipPauseTrack: 'Pausa la reproducción en YouTube Music.',
    tipResumeTrackTitle: 'Reanudar canción',
    tipResumeTrack: 'Reanuda la reproducción de esta canción en YouTube Music.',
    
    // Discovery Engine
    discoveryPanelHeading: 'Motor de Descubrimiento',
    discoveryHelpTitle: 'Guía del Motor de Descubrimiento',
    discoveryHelp: 'Combina artistas, géneros, décadas y temas con modificadores [+ Y] (incluir) y [- NO] (excluir). Los filtros estrictos garantizan que no escucharás canciones que ya tengas guardadas en tus Likes o Playlists.',
    artistModLabel: '🎤 Artistas (Y / NO):',
    artistPlaceholder: 'Escribe nombre del artista (ej. Daft Punk)...',
    genreModLabel: '🎸 Géneros (Y / NO):',
    genrePlaceholder: 'Buscar géneros (ej. indie, synth-pop)...',
    genreCatHint: '📂 Pestañas de categoría (cambia las opciones abajo):',
    decadeModLabel: '📅 Décadas (Y / NO):',
    songSeedModLabel: '🎵 Canción Semilla y Vibe:',
    songSeedPlaceholder: 'Escribe título de canción (ej. Get Lucky)...',
    blendQueueVibeBtn: '🔮 Combinar Vibe de la Cola Activa',
    strictExclusionsTitle: '🚫 FILTROS DE EXCLUSIÓN ESTRICTOS',
    notLikedSongsLabel: 'NO en Canciones que te gustan (100% Nuevo)',
    notInPlaylistsLabel: 'NO en Ninguna de mis Playlists',
    notRecentlyPlayedLabel: 'NO reproducidas recientemente:',
    notRecentlyPlayedTitle: '🕒 NO reproducidas recientemente:',
    notRecentOff: 'Desactivado',
    notRecent7d: 'Últimos 7 días',
    notRecent30d: 'Últimos 30 días',
    discLiveModeTitle: '🎤 Versiones En Vivo:',
    discLiveNotLabel: '🚫 Sin En Vivo',
    discLiveAnyLabel: 'Todas',
    discLiveOnlyLabel: '🎤 Solo En Vivo',
    discRemixModeTitle: '🎛️ Remix / Remaster:',
    discRemixNotLabel: '🚫 Sin Remix',
    discRemixAnyLabel: 'Todas',
    discRemixOnlyLabel: '🎛️ Solo Remix',
    lowPopularityLabel: '💎 Solo baja popularidad (Joyas ocultas)',
    targetTracksLabel: 'Canciones Objetivo:',
    trueShuffleAntiClumpLabel: '🔀 Aleatorio + Anti-Repetición',
    generateDiscoveryBtn: 'Generar Mezcla de Descubrimiento',
    generatingDiscoveryBtn: 'Generando Mezcla...',
    replaceMainQueueBtn: '🔄 Reemplazar Cola Principal',
    replacePlayBtn: '▶ Reemplazar y Reproducir Ya',
    appendMainQueueBtn: '➕ Agregar a la Cola Principal',
    appendSelectedBtn: '➕ Agregar Seleccionadas',
    discoveredCountBadge: '{count} descubiertas',
    discoveryToastSuccess: '🎲 ¡Se generaron {count} canciones nuevas!',
    discoveryToastReplaced: '🔄 Se reemplazó la cola activa con {count} canciones de descubrimiento',
    discoveryToastAppended: '➕ Se agregaron {count} canciones de descubrimiento al final de la cola',
    tipDiscoveryTabTitle: '¡Sorpréndeme!',
    tipDiscoveryTab: 'Genera una mezcla inteligente de descubrimiento con semillas AND/NOT, filtros estrictos de exclusión y modo aleatorio.',
    tipArtistModTitle: 'Modificador de Artista',
    tipArtistMod: 'Alterna entre [+ Y] (incluir artistas) y [- NO] (excluir artistas).',
    tipGenreModTitle: 'Modificador de Género',
    tipGenreMod: 'Alterna entre [+ Y] (incluir género) y [- NO] (excluir género).',
    tipTrackModTitle: 'Modificador de Canción Semilla',
    tipTrackMod: 'Alterna entre [+ Y] (similar a la canción) y [- NO] (disimilar).',
    tipDecadeModTitle: 'Modificador de Década',
    tipDecadeMod: 'Alterna entre [+ Y] (incluir década) y [- NO] (excluir década).',
    tipTriStateGenreTitle: 'Botones de Género de 3 Estados',
    tipTriStateGenre: 'Haz clic una vez para [+ Y] (Incluir), otra vez para [- NO] (Excluir), y una tercera para Apagar.',
    tipTriStateDecadeTitle: 'Botones de Década de 3 Estados',
    tipTriStateDecade: 'Haz clic una vez para [+ Y] (Incluir), otra vez para [- NO] (Excluir), y una tercera para Apagar.',
    tipActiveVibeTitle: 'Vibe de la Cola Activa',
    tipActiveVibe: 'Analiza los principales artistas y géneros de tu cola de escucha activa y los combina en esta mezcla de descubrimiento.',
    tipNotLikedTitle: 'NO en Canciones que te gustan',
    tipNotLiked: 'Garantiza música 100% nueva excluyendo todas las canciones guardadas en tu biblioteca.',
    tipNotInPlaylistsTitle: 'NO en Ninguna Playlist',
    tipNotInPlaylists: 'Excluye cualquier canción que ya esté guardada en cualquiera de tus playlists de YouTube Music.',
    discIgnoreBlacklistLabel: '🔓 Desbloquear Lista Negra para esta mezcla',
    tipIgnoreBlacklistTitle: 'Desbloquear Lista Negra',
    tipIgnoreBlacklist: 'Permite momentáneamente artistas bloqueados en esta mezcla sin quitarlos de tu lista negra permanente.',
    tipNotRecentTitle: 'NO Reproducidas Recientemente',
    tipNotRecent: 'Excluye canciones reproducidas en los últimos 7 o 30 días.',
    tipLiveNotTitle: 'Sin En Vivo',
    tipLiveNot: 'Excluye grabaciones en vivo, recitales, acústicos y temas en directo.',
    tipLiveOnlyTitle: 'Solo En Vivo',
    tipLiveOnly: 'Busca y filtra exclusivamente versiones en vivo, recitales y acústicos en directo.',
    tipRemixNotTitle: 'Sin Remix',
    tipRemixNot: 'Excluye versiones remix, club mix, VIP edits y remasterizaciones.',
    tipRemixOnlyTitle: 'Solo Remix / Remaster',
    tipRemixOnly: 'Busca y filtra exclusivamente remixes, club mixes, versiones VIP y temas remasterizados.',
    tipReplaceQueueTitle: 'Reemplazar Cola Principal',
    tipReplaceQueue: 'Borra todas las canciones de tu cola activa y carga todas las canciones recién descubiertas.',
    tipReplacePlayTitle: 'Reemplazar y Reproducir Inmediatamente',
    tipReplacePlay: 'Borra la cola principal, carga las canciones descubiertas e inicia la reproducción al instante en modo aleatorio.',
    tipAppendQueueTitle: 'Agregar a la Cola Principal',
    tipAppendQueue: 'Agrega todas las canciones descubiertas al final de la cola principal sin borrar las existentes.',
    tipAppendSelectedTitle: 'Agregar Canciones Seleccionadas',
    tipAppendSelected: 'Agrega únicamente las canciones marcadas al final de la cola principal.'
  }
};

function t(key, params = {}) {
  const dict = I18N[state.currentLang] || I18N.es;
  let val = dict[key] || I18N.en[key] || key;
  Object.keys(params).forEach(p => {
    val = val.replace(new RegExp(`\\{${p}\\}`, 'g'), params[p]);
  });
  return val;
}

function applyLanguage(lang) {
  state.currentLang = lang;
  localStorage.setItem('kiki_ytm_lang', lang);

  // Update language toggle button visual states
  if (DOM.langBtnEn) DOM.langBtnEn.classList.toggle('active', lang === 'en');
  if (DOM.langBtnEs) DOM.langBtnEs.classList.toggle('active', lang === 'es');

  // Subtitle badge
  const subBadge = document.querySelector('.app-sub-badge');
  if (subBadge) subBadge.textContent = t('appSubBadge');

  // Search input
  if (DOM.searchInput) {
    DOM.searchInput.placeholder = t('filterPlaceholder');
    DOM.searchInput.setAttribute('data-tooltip-title', t('tipSearchInputTitle'));
    DOM.searchInput.setAttribute('data-tooltip', t('tipSearchInput'));
  }
  if (DOM.clearSearchBtn) {
    DOM.clearSearchBtn.setAttribute('data-tooltip-title', t('tipClearFilterTitle'));
    DOM.clearSearchBtn.setAttribute('data-tooltip', t('tipClearFilter'));
  }

  // Header buttons
  if (DOM.topSyncBtn) {
    const txt = DOM.topSyncBtn.querySelector('.btn-text');
    if (txt) txt.textContent = t('syncBtn');
    DOM.topSyncBtn.setAttribute('data-tooltip-title', t('tipSyncTitle'));
    DOM.topSyncBtn.setAttribute('data-tooltip', t('tipSync'));
  }
  if (DOM.sidebarSyncBtn) {
    DOM.sidebarSyncBtn.textContent = '🔄 ' + (state.currentLang === 'es' ? 'Sincronizar' : 'Sync');
    DOM.sidebarSyncBtn.setAttribute('data-tooltip-title', t('tipSyncTitle'));
    DOM.sidebarSyncBtn.setAttribute('data-tooltip', t('tipSync'));
  }

  DOM.settingsBtn?.setAttribute('data-tooltip-title', t('tipSettingsTitle'));
  DOM.settingsBtn?.setAttribute('data-tooltip', t('tipSettings'));

  DOM.connectionBadge?.setAttribute('data-tooltip-title', t('tipConnectionTitle'));
  DOM.connectionBadge?.setAttribute('data-tooltip', t('tipConnection'));
  if (DOM.statusText) {
    if (state.authenticated) {
      DOM.statusText.textContent = t('connected') || 'Online';
    } else {
      DOM.statusText.textContent = t('loginNeeded') || 'Not Logged';
    }
  }

  // Sidebar Sections
  const sidebarTitles = document.querySelectorAll('#sidebar .section-title');
  if (sidebarTitles[0]) sidebarTitles[0].textContent = t('libraryTitle');
  if (sidebarTitles[1]) sidebarTitles[1].textContent = t('yourPlaylists');

  // Sidebar Nav Items
  const navLiked = document.querySelector('.nav-item[data-view="liked_songs"]');
  if (navLiked) {
    const lbl = navLiked.querySelector('.nav-label');
    if (lbl) lbl.textContent = t('likedSongs');
    navLiked.setAttribute('data-tooltip-title', t('tipLikedSongsTitle'));
    navLiked.setAttribute('data-tooltip', t('tipLikedSongs'));
  }
  const navAll = document.querySelector('.nav-item[data-view="all"]');
  if (navAll) {
    const lbl = navAll.querySelector('.nav-label');
    if (lbl) lbl.textContent = t('allTracks');
    navAll.setAttribute('data-tooltip-title', t('tipAllTracksTitle'));
    navAll.setAttribute('data-tooltip', t('tipAllTracks'));
  }

  const remoteHeader = document.querySelector('.remote-header');
  if (remoteHeader) remoteHeader.textContent = t('androidAccess');
  const remoteCard = document.querySelector('.remote-info-card');
  if (remoteCard) {
    remoteCard.setAttribute('data-tooltip-title', t('androidAccess'));
    remoteCard.setAttribute('data-tooltip', t('androidCardDesc'));
  }

  // Sidebar resizer
  if (DOM.sidebarResizer) {
    DOM.sidebarResizer.setAttribute('data-tooltip-title', t('tipResizeSidebarTitle'));
    DOM.sidebarResizer.setAttribute('data-tooltip', t('tipResizeSidebar'));
  }

  // Toolbar & Queue
  const queueHelpBadge = document.querySelector('.queue-help-badge');
  if (queueHelpBadge) {
    queueHelpBadge.textContent = t('queueInfoBadge');
    queueHelpBadge.setAttribute('data-tooltip-title', t('tipQueueInfoTitle'));
    queueHelpBadge.setAttribute('data-tooltip', t('tipQueueInfo'));
  }

  if (DOM.playActiveListBtn) {
    DOM.playActiveListBtn.textContent = t('playListBtn');
    DOM.playActiveListBtn.setAttribute('data-tooltip-title', t('tipPlayListTitle'));
    DOM.playActiveListBtn.setAttribute('data-tooltip', t('tipPlayList'));
  }
  if (DOM.shuffleActiveListBtn) {
    DOM.shuffleActiveListBtn.textContent = t('trueShuffleBtn');
    DOM.shuffleActiveListBtn.setAttribute('data-tooltip-title', t('tipShuffleTitle'));
    DOM.shuffleActiveListBtn.setAttribute('data-tooltip', t('tipShuffle'));
  }
  if (DOM.locatePlayingBtn) {
    DOM.locatePlayingBtn.textContent = state.currentLang === 'es' ? '🎯 Centrar en canción en curso' : '🎯 Center on Playing Song';
    DOM.locatePlayingBtn.setAttribute('data-tooltip-title', state.currentLang === 'es' ? 'Centrar en canción' : 'Center on Playing Song');
    DOM.locatePlayingBtn.setAttribute('data-tooltip', state.currentLang === 'es' ? 'Desplazar la cola para centrar la canción actualmente en reproducción.' : 'Scroll the queue to center on the currently playing song.');
  }
  if (DOM.playerLocateBtn) {
    DOM.playerLocateBtn.setAttribute('data-tooltip-title', state.currentLang === 'es' ? 'Centrar en cola' : 'Center in Queue');
    DOM.playerLocateBtn.setAttribute('data-tooltip', state.currentLang === 'es' ? 'Desplazar la lista para centrar la canción en reproducción.' : 'Scroll to center on the currently playing song in the queue.');
  }
  if (DOM.playerTrackInfo) {
    DOM.playerTrackInfo.setAttribute('data-tooltip-title', state.currentLang === 'es' ? 'Centrar en canción' : 'Center on Playing Song');
    DOM.playerTrackInfo.setAttribute('data-tooltip', state.currentLang === 'es' ? 'Haz clic para centrar la cola en esta canción.' : 'Click to scroll and center the queue on this song.');
  }
  if (DOM.reloadViewBtn) {
    DOM.reloadViewBtn.textContent = t('reloadViewBtn');
    DOM.reloadViewBtn.setAttribute('data-tooltip-title', t('tipReloadTitle'));
    DOM.reloadViewBtn.setAttribute('data-tooltip', t('tipReload'));
  }
  if (DOM.clearQueueBtn) {
    DOM.clearQueueBtn.textContent = t('clearQueueBtn');
    DOM.clearQueueBtn.setAttribute('data-tooltip-title', t('tipClearQueueTitle'));
    DOM.clearQueueBtn.setAttribute('data-tooltip', t('tipClearQueue'));
  }
  if (DOM.discPlayDirectBtn) {
    DOM.discPlayDirectBtn.textContent = t('discPlayDirectBtn');
    DOM.discPlayDirectBtn.setAttribute('data-tooltip-title', t('tipDiscPlayDirectTitle'));
    DOM.discPlayDirectBtn.setAttribute('data-tooltip', t('tipDiscPlayDirect'));
  }
  if (DOM.saveAsPlaylistBtn) {
    DOM.saveAsPlaylistBtn.textContent = t('saveAsPlaylistBtn');
  }
  if (DOM.lockOrderBtn) {
    DOM.lockOrderBtn.textContent = state.isOrderLocked ? t('lockedBtn') : t('unlockedBtn');
    DOM.lockOrderBtn.setAttribute('data-tooltip-title', t('tipLockOrderTitle'));
    DOM.lockOrderBtn.setAttribute('data-tooltip', t('tipLockOrder'));
  }
  if (DOM.resetOrderBtn) {
    DOM.resetOrderBtn.textContent = t('resetOrderBtn');
    DOM.resetOrderBtn.setAttribute('data-tooltip-title', t('tipResetOrderTitle'));
    DOM.resetOrderBtn.setAttribute('data-tooltip', t('tipResetOrder'));
  }
  if (DOM.saveOrderBtn) {
    DOM.saveOrderBtn.textContent = t('saveOrderBtn');
    DOM.saveOrderBtn.setAttribute('data-tooltip-title', t('tipSaveOrderTitle'));
    DOM.saveOrderBtn.setAttribute('data-tooltip', t('tipSaveOrder'));
  }

  // Table Column Headers
  const thNum = document.querySelector('th[data-sort="order_index"]');
  if (thNum) {
    thNum.setAttribute('data-tooltip-title', t('tipSortNumTitle'));
    thNum.setAttribute('data-tooltip', t('tipSortNum'));
  }
  const thTitle = document.querySelector('th[data-sort="title"]');
  if (thTitle) {
    const textEl = thTitle.querySelector('.col-header-text') || thTitle;
    textEl.textContent = t('colTitle');
    thTitle.setAttribute('data-tooltip-title', t('tipSortTitleTitle'));
    thTitle.setAttribute('data-tooltip', t('tipSortTitle'));
  }
  const thArtist = document.querySelector('th[data-sort="artist"]');
  if (thArtist) {
    const textEl = thArtist.querySelector('.col-header-text') || thArtist;
    textEl.textContent = t('colArtist');
    thArtist.setAttribute('data-tooltip-title', t('tipSortArtistTitle'));
    thArtist.setAttribute('data-tooltip', t('tipSortArtist'));
  }
  const thAlbum = document.querySelector('th[data-sort="album"]');
  if (thAlbum) {
    const textEl = thAlbum.querySelector('.col-header-text') || thAlbum;
    textEl.textContent = t('colAlbum');
    thAlbum.setAttribute('data-tooltip-title', t('tipSortAlbumTitle'));
    thAlbum.setAttribute('data-tooltip', t('tipSortAlbum'));
  }
  const thDur = document.querySelector('th[data-sort="duration_ms"]');
  if (thDur) {
    const textEl = thDur.querySelector('.col-header-text') || thDur;
    textEl.textContent = t('colDuration');
    thDur.setAttribute('data-tooltip-title', t('tipSortDurationTitle'));
    thDur.setAttribute('data-tooltip', t('tipSortDuration'));
  }

  // Floating Batch Action Bar
  if (DOM.batchCreatePlaylistBtn) {
    DOM.batchCreatePlaylistBtn.textContent = t('makePlaylistBtn');
    DOM.batchCreatePlaylistBtn.setAttribute('data-tooltip-title', t('tipCreatePlaylistTitle'));
    DOM.batchCreatePlaylistBtn.setAttribute('data-tooltip', t('tipCreatePlaylist'));
  }
  if (DOM.batchShuffleBtn) {
    DOM.batchShuffleBtn.textContent = t('shuffleSelectedBtn');
    DOM.batchShuffleBtn.setAttribute('data-tooltip-title', t('tipShuffleSelectedTitle'));
    DOM.batchShuffleBtn.setAttribute('data-tooltip', t('tipShuffleSelected'));
  }
  if (DOM.clearSelectionBtn) {
    DOM.clearSelectionBtn.textContent = t('clearEsc');
    DOM.clearSelectionBtn.setAttribute('data-tooltip-title', t('tipDeselectAllTitle'));
    DOM.clearSelectionBtn.setAttribute('data-tooltip', t('tipDeselectAll'));
  }

  // Right Panel
  if (DOM.tabRightSearch) {
    DOM.tabRightSearch.textContent = t('tabSearch');
    DOM.tabRightSearch.setAttribute('data-tooltip-title', t('tipSearchTabTitle'));
    DOM.tabRightSearch.setAttribute('data-tooltip', t('tipSearchTab'));
  }
  if (DOM.tabRightPlaylist) {
    DOM.tabRightPlaylist.textContent = t('tabBrowseList');
    DOM.tabRightPlaylist.setAttribute('data-tooltip-title', t('tipBrowseTabTitle'));
    DOM.tabRightPlaylist.setAttribute('data-tooltip', t('tipBrowseTab'));
  }
  if (DOM.rightSearchInput) {
    DOM.rightSearchInput.placeholder = t('searchCatalogPlaceholder');
    DOM.rightSearchInput.setAttribute('data-tooltip-title', t('tipSearchCatalogTitle'));
    DOM.rightSearchInput.setAttribute('data-tooltip', t('tipSearchCatalog'));
  }
  if (DOM.rightSearchSubmitBtn) {
    DOM.rightSearchSubmitBtn.textContent = t('searchBtn');
    DOM.rightSearchSubmitBtn.setAttribute('data-tooltip-title', t('tipRunSearchTitle'));
    DOM.rightSearchSubmitBtn.setAttribute('data-tooltip', t('tipRunSearch'));
  }
  const pillAll = document.querySelector('.search-mod-pill[data-mod="all"]');
  if (pillAll) {
    pillAll.textContent = t('modAll');
    pillAll.setAttribute('data-tooltip-title', t('tipModAllTitle'));
    pillAll.setAttribute('data-tooltip', t('tipModAll'));
  }
  const pillTrack = document.querySelector('.search-mod-pill[data-mod="track"]');
  if (pillTrack) {
    pillTrack.textContent = t('modSong');
    pillTrack.setAttribute('data-tooltip-title', t('tipModSongTitle'));
    pillTrack.setAttribute('data-tooltip', t('tipModSong'));
  }
  const pillArtist = document.querySelector('.search-mod-pill[data-mod="artist"]');
  if (pillArtist) {
    pillArtist.textContent = t('modArtist');
    pillArtist.setAttribute('data-tooltip-title', t('tipModArtistTitle'));
    pillArtist.setAttribute('data-tooltip', t('tipModArtist'));
  }
  const pillLyrics = document.querySelector('.search-mod-pill[data-mod="lyrics"]');
  if (pillLyrics) {
    pillLyrics.textContent = t('modLyrics');
    pillLyrics.setAttribute('data-tooltip-title', t('tipModLyricsTitle'));
    pillLyrics.setAttribute('data-tooltip', t('tipModLyrics'));
  }
  if (DOM.rightAddSelectedBtn) {
    DOM.rightAddSelectedBtn.textContent = t('addSelectedBtn');
    DOM.rightAddSelectedBtn.setAttribute('data-tooltip-title', t('tipAddSelectedTitle'));
    DOM.rightAddSelectedBtn.setAttribute('data-tooltip', t('tipAddSelected'));
  }

  // "🎲 Surprise Me!" Discovery Tab Translations
  if (DOM.tabRightDiscovery) {
    DOM.tabRightDiscovery.textContent = t('tabDiscovery');
    DOM.tabRightDiscovery.setAttribute('data-tooltip-title', t('tipDiscoveryTabTitle'));
    DOM.tabRightDiscovery.setAttribute('data-tooltip', t('tipDiscoveryTab'));
  }
  if (DOM.discoveryPanelHeading) {
    DOM.discoveryPanelHeading.textContent = t('discoveryPanelHeading');
  }
  if (DOM.discoveryHelpBtn) {
    DOM.discoveryHelpBtn.textContent = t('discoveryHelpBtn');
    DOM.discoveryHelpBtn.setAttribute('data-tooltip-title', t('discoveryHelpTitle'));
    DOM.discoveryHelpBtn.setAttribute('data-tooltip', t('discoveryHelp'));
  }

  const fieldLabels = document.querySelectorAll('.discovery-field-label');
  if (fieldLabels[0]) fieldLabels[0].textContent = t('artistModLabel');
  if (fieldLabels[1]) fieldLabels[1].textContent = t('genreModLabel');
  if (fieldLabels[2]) fieldLabels[2].textContent = t('decadeModLabel');
  if (fieldLabels[3]) fieldLabels[3].textContent = t('songSeedModLabel');

  if (DOM.discoveryArtistInput) DOM.discoveryArtistInput.placeholder = t('artistPlaceholder');
  if (DOM.discoveryGenreInput) DOM.discoveryGenreInput.placeholder = t('genrePlaceholder');
  if (DOM.discoveryTrackInput) DOM.discoveryTrackInput.placeholder = t('songSeedPlaceholder');
  const catHint = document.getElementById('genre-cat-hint-label');
  if (catHint) catHint.textContent = t('genreCatHint');

  if (DOM.discArtistModBtn) {
    DOM.discArtistModBtn.setAttribute('data-tooltip-title', t('tipArtistModTitle'));
    DOM.discArtistModBtn.setAttribute('data-tooltip', t('tipArtistMod'));
  }
  if (DOM.discGenreModBtn) {
    DOM.discGenreModBtn.setAttribute('data-tooltip-title', t('tipGenreModTitle'));
    DOM.discGenreModBtn.setAttribute('data-tooltip', t('tipGenreMod'));
  }
  if (DOM.discDecadeModBtn) {
    DOM.discDecadeModBtn.setAttribute('data-tooltip-title', t('tipDecadeModTitle'));
    DOM.discDecadeModBtn.setAttribute('data-tooltip', t('tipDecadeMod'));
  }
  if (DOM.discTrackModBtn) {
    DOM.discTrackModBtn.setAttribute('data-tooltip-title', t('tipTrackModTitle'));
    DOM.discTrackModBtn.setAttribute('data-tooltip', t('tipTrackMod'));
  }

  if (DOM.discoveryActiveVibeBtn) {
    DOM.discoveryActiveVibeBtn.textContent = state.discovery.useActiveVibe ? '🔮 Vibe Active' : t('blendQueueVibeBtn');
    DOM.discoveryActiveVibeBtn.setAttribute('data-tooltip-title', t('tipActiveVibeTitle'));
    DOM.discoveryActiveVibeBtn.setAttribute('data-tooltip', t('tipActiveVibe'));
  }

  const exclTitle = document.querySelector('.exclusions-card-title');
  if (exclTitle) exclTitle.textContent = t('strictExclusionsTitle');

  const notLikedLabel = DOM.discNotLiked?.parentElement?.querySelector('.switch-label');
  if (notLikedLabel) notLikedLabel.textContent = t('notLikedSongsLabel');
  DOM.discNotLiked?.parentElement?.setAttribute('data-tooltip-title', t('tipNotLikedTitle'));
  DOM.discNotLiked?.parentElement?.setAttribute('data-tooltip', t('tipNotLiked'));

  const notPlLabel = DOM.discNotPlaylists?.parentElement?.querySelector('.switch-label');
  if (notPlLabel) notPlLabel.textContent = t('notInPlaylistsLabel');
  DOM.discNotPlaylists?.parentElement?.setAttribute('data-tooltip-title', t('tipNotInPlaylistsTitle'));
  DOM.discNotPlaylists?.parentElement?.setAttribute('data-tooltip', t('tipNotInPlaylists'));

  const ignoreBlLabel = document.getElementById('disc-ignore-blacklist-label');
  if (ignoreBlLabel) ignoreBlLabel.textContent = t('discIgnoreBlacklistLabel');
  DOM.discIgnoreBlacklist?.parentElement?.setAttribute('data-tooltip-title', t('tipIgnoreBlacklistTitle'));
  DOM.discIgnoreBlacklist?.parentElement?.setAttribute('data-tooltip', t('tipIgnoreBlacklist'));

  const discRecentTitle = document.getElementById('disc-recent-title');
  if (discRecentTitle) discRecentTitle.textContent = t('notRecentlyPlayedTitle');
  const discRecentOff = document.getElementById('disc-recent-off-label');
  if (discRecentOff) discRecentOff.textContent = t('notRecentOff');
  const discRecent7d = document.getElementById('disc-recent-7d-label');
  if (discRecent7d) discRecent7d.textContent = t('notRecent7d');
  const discRecent30d = document.getElementById('disc-recent-30d-label');
  if (discRecent30d) discRecent30d.textContent = t('notRecent30d');
  document.querySelector('.discovery-sub-block')?.setAttribute('data-tooltip-title', t('tipNotRecentTitle'));
  document.querySelector('.discovery-sub-block')?.setAttribute('data-tooltip', t('tipNotRecent'));

  const discLiveTitle = document.getElementById('disc-live-mode-title');
  if (discLiveTitle) discLiveTitle.textContent = t('discLiveModeTitle');
  const discLiveNot = document.getElementById('disc-live-not-label');
  if (discLiveNot) discLiveNot.textContent = t('discLiveNotLabel');
  const discLiveAny = document.getElementById('disc-live-any-label');
  if (discLiveAny) discLiveAny.textContent = t('discLiveAnyLabel');
  const discLiveOnly = document.getElementById('disc-live-only-label');
  if (discLiveOnly) discLiveOnly.textContent = t('discLiveOnlyLabel');

  const discRemixTitle = document.getElementById('disc-remix-mode-title');
  if (discRemixTitle) discRemixTitle.textContent = t('discRemixModeTitle');
  const discRemixNot = document.getElementById('disc-remix-not-label');
  if (discRemixNot) discRemixNot.textContent = t('discRemixNotLabel');
  const discRemixAny = document.getElementById('disc-remix-any-label');
  if (discRemixAny) discRemixAny.textContent = t('discRemixAnyLabel');
  const discRemixOnly = document.getElementById('disc-remix-only-label');
  if (discRemixOnly) discRemixOnly.textContent = t('discRemixOnlyLabel');

  if (DOM.discLowPopLabel) DOM.discLowPopLabel.textContent = t('lowPopularityLabel');

  const queueSizeLbl = document.querySelector('.queue-size-label');
  if (queueSizeLbl) queueSizeLbl.textContent = t('targetTracksLabel');

  const shuffleLbl = DOM.discTrueShuffle?.parentElement?.querySelector('.switch-label');
  if (shuffleLbl) shuffleLbl.textContent = t('trueShuffleAntiClumpLabel');

  const genBtnText = DOM.discoveryGenerateBtn?.querySelector('.btn-text');
  if (genBtnText) genBtnText.textContent = state.discovery.isGenerating ? t('generatingDiscoveryBtn') : t('generateDiscoveryBtn');

  if (DOM.discReplaceQueueBtn) {
    DOM.discReplaceQueueBtn.textContent = t('replaceMainQueueBtn');
    DOM.discReplaceQueueBtn.setAttribute('data-tooltip-title', t('tipReplaceQueueTitle'));
    DOM.discReplaceQueueBtn.setAttribute('data-tooltip', t('tipReplaceQueue'));
  }
  if (DOM.discReplacePlayBtn) {
    DOM.discReplacePlayBtn.textContent = t('replacePlayBtn');
    DOM.discReplacePlayBtn.setAttribute('data-tooltip-title', t('tipReplacePlayTitle'));
    DOM.discReplacePlayBtn.setAttribute('data-tooltip', t('tipReplacePlay'));
  }
  if (DOM.discAppendQueueBtn) {
    DOM.discAppendQueueBtn.textContent = t('appendMainQueueBtn');
    DOM.discAppendQueueBtn.setAttribute('data-tooltip-title', t('tipAppendQueueTitle'));
    DOM.discAppendQueueBtn.setAttribute('data-tooltip', t('tipAppendQueue'));
  }
  if (DOM.discAppendSelectedBtn) {
    DOM.discAppendSelectedBtn.textContent = t('appendSelectedBtn');
    DOM.discAppendSelectedBtn.setAttribute('data-tooltip-title', t('tipAppendSelectedTitle'));
    DOM.discAppendSelectedBtn.setAttribute('data-tooltip', t('tipAppendSelected'));
  }

  // Player controls
  DOM.ctrlPrev?.setAttribute('data-tooltip-title', t('tipPrevTitle'));
  DOM.ctrlPrev?.setAttribute('data-tooltip', t('tipPrev'));
  DOM.ctrlPlaypause?.setAttribute('data-tooltip-title', t('tipPlayPauseTitle'));
  DOM.ctrlPlaypause?.setAttribute('data-tooltip', t('tipPlayPause'));
  DOM.ctrlNext?.setAttribute('data-tooltip-title', t('tipNextTitle'));
  DOM.ctrlNext?.setAttribute('data-tooltip', t('tipNext'));
  DOM.ctrlTrueShuffle?.setAttribute('data-tooltip-title', t('tipShuffleTitle'));
  DOM.ctrlTrueShuffle?.setAttribute('data-tooltip', t('tipShuffle'));
  if (DOM.ctrlTrueShuffle) DOM.ctrlTrueShuffle.textContent = t('trueShuffleBtn');

  // Context Menus
  const ctxPlay = document.getElementById('ctx-play-now');
  if (ctxPlay) ctxPlay.textContent = t('ctxPlayFromHere');
  const ctxToggleLike = document.getElementById('ctx-toggle-like');
  if (ctxToggleLike) {
    const track = state.tracks[contextMenuIndex];
    const isLiked = track ? (state.likedTrackIds.has(track.id) || Boolean(track.is_liked)) : false;
    ctxToggleLike.textContent = isLiked ? t('ctxRemoveFromLiked') : t('ctxSaveToLiked');
  }
  const ctxShuffle = document.getElementById('ctx-true-shuffle');
  if (ctxShuffle) ctxShuffle.textContent = t('ctxTrueShuffleSelected');
  const ctxPl = document.getElementById('ctx-make-playlist');
  if (ctxPl) ctxPl.textContent = t('ctxMakePlaylist');
  const ctxRemFromList = document.getElementById('ctx-remove-from-list');
  if (ctxRemFromList) ctxRemFromList.textContent = t('ctxRemoveFromList');
  const ctxKeepOnly = document.getElementById('ctx-keep-only-selected');
  if (ctxKeepOnly) ctxKeepOnly.textContent = t('ctxKeepOnlySelected');

  const ctxPlRename = document.getElementById('ctx-playlist-rename');
  if (ctxPlRename) ctxPlRename.textContent = t('ctxRenamePlaylist');
  const ctxPlDelete = document.getElementById('ctx-playlist-delete');
  if (ctxPlDelete) ctxPlDelete.textContent = t('ctxDeletePlaylist');

  // Sync loading hero & connect welcome hero
  if (DOM.syncStatusTitle) DOM.syncStatusTitle.textContent = t('syncHeroTitle');
  if (DOM.syncStatusDesc) DOM.syncStatusDesc.textContent = t('syncHeroDesc');
  if (DOM.syncStageText && (!state.isSyncing || DOM.syncStageText.textContent.includes('Fetching') || DOM.syncStageText.textContent.includes('Obteniendo'))) {
    DOM.syncStageText.textContent = t('syncStageFetchingLiked');
  }
  const heroTitle = DOM.connectWelcomeHero?.querySelector('h2');
  if (heroTitle) heroTitle.textContent = t('connectHeroTitle');
  const heroDesc = DOM.connectWelcomeHero?.querySelector('p');
  if (heroDesc) heroDesc.textContent = t('connectHeroDesc');
  const heroBtn = DOM.heroLoginBtn;
  if (heroBtn) heroBtn.textContent = t('connectHeroBtn');

  // Dynamic UI re-render
  updateCounts(state.tracks.length);
  updateSelectionUI();
}

// --- Sync State & Auto-Sync Management ---
function showSyncState(stageText) {
  state.isSyncing = true;
  if (DOM.syncLoadingHero) {
    DOM.syncLoadingHero.classList.remove('hidden');
  }
  DOM.connectWelcomeHero?.classList.add('hidden');
  DOM.emptyState?.classList.add('hidden');
  DOM.tracksTable?.classList.add('hidden');
  if (DOM.syncStageText && stageText) {
    DOM.syncStageText.textContent = stageText;
  }
}

function hideSyncState() {
  state.isSyncing = false;
  DOM.syncLoadingHero?.classList.add('hidden');
  if (state.authenticated) {
    DOM.connectWelcomeHero?.classList.add('hidden');
    DOM.tracksTable?.classList.remove('hidden');
  } else {
    DOM.connectWelcomeHero?.classList.remove('hidden');
    DOM.tracksTable?.classList.add('hidden');
  }
}

let isSyncInProgress = false;
async function triggerAutoSync(silent = false) {
  if (isSyncInProgress) return;
  isSyncInProgress = true;

  if (!silent) {
    showSyncState(t('syncStageFetchingLiked'));
  } else {
    state.isSyncing = true;
  }

  let poller = setInterval(async () => {
    try {
      const syncStatus = await api('/api/sync/status');
      if (syncStatus && syncStatus.is_syncing && syncStatus.status_message) {
        if (DOM.syncStageText) {
          DOM.syncStageText.textContent = syncStatus.status_message;
        }
      }
    } catch (e) {}
  }, 700);

  try {
    const res = await api('/api/sync', { method: 'POST' });
    clearInterval(poller);

    if (res.warning) {
      showToast('⚠️ ' + res.warning, 'error');
    } else {
      const likedCount = res.liked_count || 0;
      const plCount = res.playlists_count || 0;
      const isEs = state.currentLang === 'es';
      const msg = isEs 
        ? `✅ ¡Sincronizadas ${likedCount} canciones y ${plCount} playlists!` 
        : `✅ Synced ${likedCount} Liked Songs and ${plCount} playlists!`;
      showToast(msg);
    }
    localStorage.setItem('kiki_has_ever_synced', 'true');
    await checkStatus();
    await loadLikedTrackIds();
    await loadPlaylists();
    await loadTracks();
  } catch (e) {
    clearInterval(poller);
    showToast('Sync error: ' + e.message, 'error');
  } finally {
    isSyncInProgress = false;
    hideSyncState();
  }
}

// --- Background Auth Poller (for external browser login) ---
let authPollerTimer = null;
function startAuthPoller() {
  if (authPollerTimer) return;
  authPollerTimer = setInterval(async () => {
    if (state.authenticated || state.isSyncing) return;
    try {
      const data = await api('/api/status');
      if (data && data.authenticated) {
        stopAuthPoller();
        await checkStatus();
        await loadLikedTrackIds();
        await loadPlaylists();
        await loadTracks();
        triggerAutoSync();
      }
    } catch (e) {}
  }, 2000);
}

function stopAuthPoller() {
  if (authPollerTimer) {
    clearInterval(authPollerTimer);
    authPollerTimer = null;
  }
}

// --- App Initialization ---
document.addEventListener('DOMContentLoaded', async () => {
  try { applyLanguage(state.currentLang); } catch (e) { console.error('applyLanguage error:', e); }
  try { TooltipManager.init(); } catch (e) { console.error('TooltipManager error:', e); }
  try { initEventListeners(); } catch (e) { console.error('initEventListeners error:', e); }
  try { initRightPanel(); } catch (e) { console.error('initRightPanel error:', e); }
  try { initPanelResizers(); } catch (e) { console.error('initPanelResizers error:', e); }
  try { initTableColumnResizing(); } catch (e) { console.error('initTableColumnResizing error:', e); }
  try { initWorkspaceContainerDrop(); } catch (e) { console.error('initWorkspaceContainerDrop error:', e); }

  const isAuthRedirect = window.location.search.includes('auth=success');
  if (isAuthRedirect) {
    window.history.replaceState({}, document.title, window.location.pathname);
  }

  const statusData = await checkStatus();
  if (state.authenticated) {
    try { await loadLikedTrackIds(); } catch (e) {}
    try { await loadPlaylists(); } catch (e) {}
    try { await loadTracks(); } catch (e) {}
    try { await refreshBlacklistCount(); } catch (e) {}
    
    // If just authorized or library has never been synced, automatically trigger sync!
    const hasEverSynced = localStorage.getItem('kiki_has_ever_synced') === 'true';
    if (isAuthRedirect || !hasEverSynced || !statusData?.has_synced_tracks || (state.tracks.length === 0 && state.allPlaylists.length === 0)) {
      triggerAutoSync();
    }
  } else {
    // If not yet authenticated, start background poller so login window completes automatically
    startAuthPoller();
  }
  try { startPlayerStatePoller(); } catch (e) {}
});

// --- API Helper ---
async function api(endpoint, options = {}) {
  try {
    const res = await fetch(endpoint, {
      headers: { 'Content-Type': 'application/json' },
      ...options
    });
    if (!res.ok) {
      const errData = await res.json().catch(() => ({ detail: res.statusText }));
      throw new Error(errData.detail || 'API request failed');
    }
    return await res.json();
  } catch (err) {
    console.error(`API Error on ${endpoint}:`, err);
    throw err;
  }
}

// --- Draggable Panel Resizers ---
function initPanelResizers() {
  if (DOM.sidebarResizer && DOM.sidebar) {
    let isDragging = false;
    DOM.sidebarResizer.addEventListener('mousedown', (e) => {
      isDragging = true;
      DOM.sidebarResizer.classList.add('resizing');
      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';
    });

    window.addEventListener('mousemove', (e) => {
      if (!isDragging) return;
      const newWidth = Math.max(140, Math.min(420, e.clientX));
      DOM.sidebar.style.width = `${newWidth}px`;
      DOM.sidebar.style.minWidth = `${newWidth}px`;
    });

    window.addEventListener('mouseup', () => {
      if (isDragging) {
        isDragging = false;
        DOM.sidebarResizer.classList.remove('resizing');
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
      }
    });
  }

  if (DOM.rightPanelResizer && DOM.rightPanel) {
    let isDragging = false;
    DOM.rightPanelResizer.addEventListener('mousedown', (e) => {
      isDragging = true;
      DOM.rightPanelResizer.classList.add('resizing');
      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';
    });

    window.addEventListener('mousemove', (e) => {
      if (!isDragging) return;
      const newWidth = Math.max(220, Math.min(550, window.innerWidth - e.clientX));
      DOM.rightPanel.style.width = `${newWidth}px`;
      DOM.rightPanel.style.minWidth = `${newWidth}px`;
    });

    window.addEventListener('mouseup', () => {
      if (isDragging) {
        isDragging = false;
        DOM.rightPanelResizer.classList.remove('resizing');
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
      }
    });
  }
}

// --- Table Column Resizing (Dynamic responsive auto-fit + manual widening) ---
function initTableColumnResizing() {
  const table = document.getElementById('tracks-table');
  if (!table) return;

  const MIN_WIDTHS = {
    num: 32,
    title: 80,
    artist: 60,
    album: 50,
    like: 32,
    duration: 42
  };

  // 1. Restore saved column widths ONLY if explicitly customized by user
  let savedWidths = null;
  try {
    const raw = localStorage.getItem('kiki_column_widths_v2');
    if (raw) savedWidths = JSON.parse(raw);
  } catch (e) {}

  const ths = Array.from(table.querySelectorAll('thead th'));

  if (savedWidths && typeof savedWidths === 'object') {
    let totalExplicitWidth = 0;
    ths.forEach(th => {
      const colKey = th.dataset.col;
      if (colKey && savedWidths[colKey]) {
        const w = Math.max(MIN_WIDTHS[colKey] || 32, savedWidths[colKey]);
        th.style.width = `${w}px`;
        totalExplicitWidth += w;
      }
    });
    if (totalExplicitWidth > 0) {
      table.style.width = `${totalExplicitWidth}px`;
    }
  } else {
    // Clean up any stale inline widths so dynamic responsive CSS rules apply
    ths.forEach(th => th.style.width = '');
    table.style.width = '';
  }

  // 2. Attach resize events to each column's resizer handle
  ths.forEach(th => {
    const colKey = th.dataset.col;
    let resizer = th.querySelector('.col-resizer');
    if (!resizer && colKey && colKey !== 'duration') {
      resizer = document.createElement('div');
      resizer.className = 'col-resizer';
      resizer.title = 'Drag to resize column (double-click to reset to dynamic auto-fit)';
      th.appendChild(resizer);
    }
    if (!resizer) return;

    resizer.addEventListener('click', (e) => e.stopPropagation());

    // Double click to reset all columns back to dynamic responsive percentages
    resizer.addEventListener('dblclick', (e) => {
      e.stopPropagation();
      e.preventDefault();
      ths.forEach(t => t.style.width = '');
      table.style.width = '';
      try {
        localStorage.removeItem('kiki_column_widths_v2');
        localStorage.removeItem('kiki_column_widths');
      } catch (err) {}
      showToast('🔄 Column widths reset to dynamic auto-fit');
    });

    resizer.addEventListener('mousedown', (e) => {
      e.stopPropagation();
      e.preventDefault();

      // Ensure all headers have explicit pixel widths before dragging
      let currentTotal = 0;
      ths.forEach(otherTh => {
        const curW = otherTh.offsetWidth;
        otherTh.style.width = `${curW}px`;
        currentTotal += curW;
      });
      table.style.width = `${currentTotal}px`;

      const startX = e.clientX;
      const startWidth = th.offsetWidth;
      const startTableWidth = table.offsetWidth;
      const minW = MIN_WIDTHS[colKey] || 32;

      resizer.classList.add('is-resizing');
      document.body.classList.add('is-col-resizing');

      function onMouseMove(moveEvent) {
        const delta = moveEvent.clientX - startX;
        const newWidth = Math.max(minW, startWidth + delta);
        th.style.width = `${newWidth}px`;
        table.style.width = `${startTableWidth + (newWidth - startWidth)}px`;
      }

      function onMouseUp() {
        document.removeEventListener('mousemove', onMouseMove);
        document.removeEventListener('mouseup', onMouseUp);
        resizer.classList.remove('is-resizing');
        document.body.classList.remove('is-col-resizing');
        saveAllColWidths();
      }

      document.addEventListener('mousemove', onMouseMove);
      document.addEventListener('mouseup', onMouseUp);
    });
  });

  function saveAllColWidths() {
    try {
      const data = {};
      let totalW = 0;
      ths.forEach(th => {
        const key = th.dataset.col;
        if (key) {
          const w = th.offsetWidth;
          data[key] = w;
          totalW += w;
        }
      });
      table.style.width = `${totalW}px`;
      localStorage.setItem('kiki_column_widths_v2', JSON.stringify(data));
    } catch (e) {}
  }
}

// --- Check Status ---
async function checkStatus() {
  try {
    const data = await api('/api/status');
    state.authenticated = data.authenticated;
    state.isMac = data.is_mac;
    state.hasSyncedTracks = !!data.has_synced_tracks;
    
    if (DOM.remoteUrlDisplay) {
      DOM.remoteUrlDisplay.textContent = data.access_url;
    }
    
    if (data.authenticated) {
      DOM.connectionBadge.className = 'status-badge status-online';
      DOM.statusText.textContent = t('connected') || 'Online';
      DOM.loginBtn?.classList.add('hidden');
      DOM.connectWelcomeHero?.classList.add('hidden');
      if (!state.isSyncing) {
        DOM.tracksTable?.classList.remove('hidden');
      }
      if (data.total_tracks !== undefined) {
        const countAll = document.getElementById('count-all');
        if (countAll) countAll.textContent = data.total_tracks;
      }
      if (data.liked_tracks !== undefined) {
        const countLiked = document.getElementById('count-liked');
        if (countLiked) countLiked.textContent = data.liked_tracks;
      }
      loadDevices();
      stopAuthPoller();
    } else {
      DOM.connectionBadge.className = 'status-badge status-offline';
      DOM.statusText.textContent = t('loginNeeded') || 'Not Logged';
      if (!state.isSyncing) {
        DOM.connectWelcomeHero?.classList.remove('hidden');
        DOM.tracksTable?.classList.add('hidden');
        DOM.emptyState?.classList.add('hidden');
      }
      DOM.tracksTbody.innerHTML = '';
      DOM.playlistsList.innerHTML = `<div style="padding:12px 16px; font-size:11px; color:#888;">${t('connectPromptPlaylists') || 'Connect YouTube Music to load your playlists.'}</div>`;
      startAuthPoller();
    }
    return data;
  } catch (e) {
    DOM.connectionBadge.className = 'status-badge status-offline';
    DOM.statusText.textContent = t('offline') || 'Offline';
    startAuthPoller();
    return null;
  }
}

async function loadDevices() {
  try {
    const data = await api('/api/player/devices');
    const select = DOM.deviceSelect;
    if (!select) return;
    select.innerHTML = '<option value="">📱 Select Device</option>';
    (data.devices || []).forEach(d => {
      const opt = document.createElement('option');
      opt.value = d.id;
      opt.textContent = `${d.is_active ? '🟢 ' : ''}${d.name} (${d.type})`;
      if (d.is_active) opt.selected = true;
      select.appendChild(opt);
    });
  } catch (e) {}
}

function openPlaylistContextMenu(x, y, playlist) {
  activeContextMenuPlaylist = playlist;
  hideAllContextMenus();
  const menu = DOM.playlistContextMenu;
  menu.style.left = `${Math.min(x, window.innerWidth - 190)}px`;
  menu.style.top = `${Math.min(y, window.innerHeight - 180)}px`;
  menu.classList.remove('hidden');
}

// --- Interactive Confirmation & Rename Modal ---
function showConfirmModal({ title, message, showInput = false, inputValue = '', inputLabel = 'Name:', confirmText = 'Confirm', onConfirm }) {
  DOM.confirmModalTitle.textContent = title;
  DOM.confirmModalMessage.textContent = message || '';
  
  if (showInput) {
    DOM.confirmModalInputGroup.classList.remove('hidden');
    DOM.confirmModalInputLabel.textContent = inputLabel;
    DOM.confirmModalInput.value = inputValue;
  } else {
    DOM.confirmModalInputGroup.classList.add('hidden');
  }

  DOM.confirmModalSubmitBtn.textContent = confirmText;
  confirmModalCallback = onConfirm;
  DOM.confirmModal.classList.remove('hidden');
  if (showInput) DOM.confirmModalInput.focus();
}

function attachNavItemDropHandler(item) {
  if (!item || item._dropAttached) return;
  item._dropAttached = true;

  item.addEventListener('dragover', (e) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'copy';
    item.classList.add('nav-drop-target');
  });

  item.addEventListener('dragleave', () => {
    item.classList.remove('nav-drop-target');
  });

  item.addEventListener('drop', async (e) => {
    e.preventDefault();
    e.stopPropagation();
    item.classList.remove('nav-drop-target');

    let payload = null;
    try {
      const raw = e.dataTransfer.getData('application/json') || e.dataTransfer.getData('text/plain');
      payload = raw ? JSON.parse(raw) : null;
    } catch (err) {}

    if (!payload && window.__draggedTracksFromRight) {
      payload = { source: 'right', tracks: window.__draggedTracksFromRight };
    }
    window.__draggedTracksFromRight = null;

    if (!payload || !payload.tracks || payload.tracks.length === 0) return;

    const view = item.dataset.view;
    const playlistId = item.dataset.playlistId;

    if (view === 'blacklist') {
      await handleDropTracksOntoBlacklist(payload.tracks);
    } else if (view === 'liked_songs') {
      for (const t of payload.tracks) {
        try {
          await api('/api/playlists/liked_songs/add-track', {
            method: 'POST',
            body: JSON.stringify({
              track_id: t.id,
              track: {
                id: t.id,
                uri: t.uri || `yt:track:${t.id}`,
                title: t.title,
                artist: t.artist,
                primary_artist: t.primary_artist || '',
                album: t.album || '',
                duration_ms: t.duration_ms || t.durationMs || 0,
                durationMs: t.duration_ms || t.durationMs || 0,
                thumbnailUrl: t.thumbnailUrl || t.album_art_url || '',
                album_art_url: t.album_art_url || t.thumbnailUrl || '',
                loudnessDb: t.loudnessDb || 0.0
              }
            })
          });
          state.likedTrackIds.add(t.id);
        } catch (err) {}
      }
      await loadPlaylists();
      if (state.activeView === 'liked_songs') await loadTracks();
      showToast(state.currentLang === 'es'
        ? `💚 ${payload.tracks.length} canción(es) añadida(s) a Canciones que te gustan`
        : `💚 Added ${payload.tracks.length} track(s) to Liked Songs`);
    } else if (playlistId) {
      for (const t of payload.tracks) {
        try {
          await api(`/api/playlists/${playlistId}/add-track`, {
            method: 'POST',
            body: JSON.stringify({
              track_id: t.id,
              track: {
                id: t.id,
                uri: t.uri || `yt:track:${t.id}`,
                title: t.title,
                artist: t.artist,
                primary_artist: t.primary_artist || '',
                album: t.album || '',
                duration_ms: t.duration_ms || t.durationMs || 0,
                durationMs: t.duration_ms || t.durationMs || 0,
                thumbnailUrl: t.thumbnailUrl || t.album_art_url || '',
                album_art_url: t.album_art_url || t.thumbnailUrl || '',
                loudnessDb: t.loudnessDb || 0.0
              }
            })
          });
        } catch (err) {}
      }
      await loadPlaylists();
      if (state.activeView === playlistId) await loadTracks();
      showToast(state.currentLang === 'es'
        ? `➕ ${payload.tracks.length} canción(es) añadida(s) a la playlist`
        : `➕ Added ${payload.tracks.length} track(s) to playlist`);
    }
  });
}

// --- Playlists Operations ---
async function loadPlaylists() {
  try {
    const data = await api('/api/playlists');
    state.allPlaylists = data.playlists || [];
    DOM.playlistsList.innerHTML = '';
    
    // Update Liked Songs count from the liked_songs playlist record
    const likedPl = state.allPlaylists.find(p => p.id === 'liked_songs');
    if (likedPl) {
      const countLiked = document.getElementById('count-liked');
      if (countLiked) {
        countLiked.textContent = likedPl.total_tracks || (likedPl.track_ids ? likedPl.track_ids.length : 0);
      }
    }

    if (DOM.rightPlaylistPicker) {
      DOM.rightPlaylistPicker.innerHTML = `<option value="">${t('selectPlaylistOption')}</option>`;
    }

    state.allPlaylists.forEach(p => {
      if (DOM.rightPlaylistPicker) {
        const opt = document.createElement('option');
        opt.value = p.id;
        opt.textContent = `${p.name} (${p.total_tracks} ${state.currentLang === 'es' ? 'canciones' : 'songs'})`;
        DOM.rightPlaylistPicker.appendChild(opt);
      }

      if (p.id === 'liked_songs') return;

      const isSynced = Boolean(p.is_synced);
      const syncBadgeHtml = isSynced
        ? ''
        : `<button class="playlist-sync-badge unsynced" title="Unsynced changes - click to sync with YouTube">🔄</button>`;

      const item = document.createElement('div');
      item.className = `nav-item ${state.activeView === p.id ? 'active' : ''}`;
      item.dataset.playlistId = p.id;
      item.innerHTML = `
        <span class="nav-icon">📑</span>
        <span class="nav-label" style="overflow:hidden; text-overflow:ellipsis; white-space:nowrap; flex:1;">${escapeHtml(p.name)}</span>
        <span class="nav-count" style="margin-right:4px;">${p.total_tracks}</span>
        ${syncBadgeHtml}
      `;

      const syncBtn = item.querySelector('.playlist-sync-badge.unsynced');
      if (syncBtn) {
        syncBtn.addEventListener('click', (e) => {
          e.stopPropagation();
          handleSyncPlaylist(p);
        });
      }

      item.addEventListener('click', () => {
        document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
        item.classList.add('active');
        state.activeView = p.id;
        loadTracks();
      });

      // Right-Click Context Menu for Playlists (Rename, Delete)
      item.addEventListener('contextmenu', (e) => {
        e.preventDefault();
        e.stopPropagation();
        openPlaylistContextMenu(e.clientX, e.clientY, p);
      });

      attachNavItemDropHandler(item);
      DOM.playlistsList.appendChild(item);
    });
    updateSaveAsPlaylistButtonState();
  } catch (e) {}
}

// --- Load Tracks for Current View (Main Center Panel) ---
async function loadTracks() {
  try {
    if (state.activeView === 'blacklist') {
      DOM.queueActionsRow?.classList.add('hidden');
      DOM.blacklistToolbarRow?.classList.remove('hidden');
      DOM.tracksTable?.classList.add('hidden');
      DOM.blacklistContainer?.classList.remove('hidden');
      await loadBlacklist();
      return;
    } else {
      DOM.queueActionsRow?.classList.remove('hidden');
      DOM.blacklistToolbarRow?.classList.add('hidden');
      DOM.tracksTable?.classList.remove('hidden');
      DOM.blacklistContainer?.classList.add('hidden');
    }

    let url = '/api/tracks?';
    const params = new URLSearchParams();
    
    if (state.activeView === 'liked_songs') {
      params.append('playlist_id', 'liked_songs');
    } else if (state.activeView !== 'all') {
      params.append('playlist_id', state.activeView);
    }

    if (state.searchQuery.trim()) {
      params.append('search', state.searchQuery.trim());
    }

    params.append('sort_by', state.sortState.column);
    params.append('sort_direction', state.sortState.direction);

    const data = await api(`${url}${params.toString()}`);
    state.tracks = (data.tracks || []).filter(t => !isPrivateOrDeletedTrack(t));

    if (state.activeView === 'liked_songs') {
      state.tracks.forEach(t => state.likedTrackIds.add(t.id));
    } else if (state.likedTrackIds.size === 0 && state.authenticated) {
      loadLikedTrackIds().catch(() => {});
    }

    if (state.sortState.column === 'order_index' && !state.sortState.isTemporary) {
      state.userCustomOrderIds = state.tracks.map(t => t.id);
    }

    renderTracksTable();
    updateCounts(data.count);
  } catch (e) {
    showToast('Error loading tracks: ' + e.message, 'error');
  }
}

function updateCounts(count) {
  const isEs = state.currentLang === 'es';

  if (state.activeView === 'blacklist') {
    const c = count !== undefined ? count : state.blacklist.length;
    DOM.trackCountBadge.textContent = isEs 
      ? `${c} artista${c === 1 ? '' : 's'} en la lista negra` 
      : `${c} blacklisted artist${c === 1 ? '' : 's'}`;

    const headingEl = document.getElementById('active-queue-heading');
    const subtextEl = document.getElementById('queue-subtext-desc');

    if (headingEl) {
      headingEl.textContent = isEs ? 'Cola activa: Blacklist' : 'Active Queue: Blacklist';
    }

    if (subtextEl) {
      subtextEl.textContent = isEs
        ? 'Artistas excluidos de las mezclas de Surprise Me. Se permiten colaboraciones donde no sean el artista principal.'
        : 'Artists excluded from Surprise Me discovery mixes. Collaborative tracks where they are not the main artist remain allowed.';
    }

    const countBlacklist = document.getElementById('count-blacklist');
    if (countBlacklist) {
      countBlacklist.textContent = c;
    }
    return;
  }

  DOM.trackCountBadge.textContent = isEs 
    ? `${count} canción${count === 1 ? '' : 'es'} en la cola` 
    : `${count} track${count === 1 ? '' : 's'} in queue`;

  const headingEl = document.getElementById('active-queue-heading');
  const subtextEl = document.getElementById('queue-subtext-desc');

  if (headingEl) {
    let viewName = t('likedSongs');
    if (state.activeView === 'all') viewName = t('allTracks');
    else {
      const pl = state.allPlaylists.find(p => p.id === state.activeView);
      if (pl) viewName = pl.name;
    }

    const queuePrefix = isEs ? 'Cola activa' : 'Active Queue';
    headingEl.textContent = `${queuePrefix}: ${viewName}`;
  }

  if (subtextEl) {
    subtextEl.textContent = t('queueSubtextWithCount', { count });
  }

  const countLiked = document.getElementById('count-liked');
  const countAll = document.getElementById('count-all');
  if (countLiked && state.activeView === 'liked_songs' && !state.searchQuery) {
    countLiked.textContent = count;
  }
  if (countAll && state.activeView === 'all' && !state.searchQuery) {
    countAll.textContent = count;
  }
}

function isPrivateOrDeletedTrack(t) {
  if (!t || !t.title) return true;
  const clean = t.title.trim().toLowerCase().replace(/^\[|\]$/g, '').trim();
  return clean === 'private video' || 
         clean === 'deleted video' || 
         clean === 'vídeo privado' || 
         clean === 'vídeo eliminado' || 
         clean === 'video privado' || 
         clean === 'video eliminado' ||
         clean.startsWith('private video') ||
         clean.startsWith('deleted video') ||
         clean.startsWith('vídeo privado') ||
         clean.startsWith('vídeo eliminado') ||
         clean.startsWith('video privado') ||
         clean.startsWith('video eliminado');
}

// --- Render Table: Check | Num | Title | Artist | Album | Dur ---
function renderTracksTable() {
  DOM.tracksTbody.innerHTML = '';
  
  if (state.tracks.length === 0) {
    if (state.authenticated && !state.isSyncing) {
      DOM.connectWelcomeHero?.classList.add('hidden');
      DOM.tracksTable?.classList.add('hidden');
      DOM.emptyState?.classList.remove('hidden');
    }
    updateSaveAsPlaylistButtonState();
    return;
  } else {
    DOM.emptyState?.classList.add('hidden');
    DOM.connectWelcomeHero?.classList.add('hidden');
    DOM.syncLoadingHero?.classList.add('hidden');
    DOM.tracksTable?.classList.remove('hidden');
  }

  const fragment = document.createDocumentFragment();

  state.tracks.forEach((track, index) => {
    if (isPrivateOrDeletedTrack(track)) return;

    const isSelected = state.selectedIds.has(track.id);
    
    // Strictly match by ID or URI to avoid marking all songs with the same title
    const isNowPlaying = Boolean(
      state.currentPlayingTrackId && (track.id === state.currentPlayingTrackId || (track.uri && track.uri === state.currentPlayingTrackId))
    );

    const tr = document.createElement('tr');
    tr.className = `track-row ${isSelected ? 'selected' : ''} ${isNowPlaying ? 'now-playing-row' : ''}`;
    tr.dataset.trackId = track.id;
    tr.dataset.trackUri = track.uri;
    tr.dataset.trackTitle = track.title;
    tr.dataset.index = index;
    tr.draggable = !state.isOrderLocked;

    // Col 1: Play Symbol / Playing Indicator (replaces row number)
    const tdPlay = document.createElement('td');
    tdPlay.className = 'col-num track-num-cell';
    const playBtnTitle = isNowPlaying
      ? (state.currentLang === 'es' ? 'Pausar/Reanudar reproducción' : 'Pause/Resume playback')
      : (state.currentLang === 'es' ? 'Reproducir desde esta canción' : 'Play from this song');

    const playBtn = document.createElement('button');
    playBtn.className = `row-play-btn ${isNowPlaying ? 'playing' : ''}`;
    playBtn.title = playBtnTitle;
    playBtn.textContent = isNowPlaying ? '🔊' : '▶';
    playBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      playFromIndex(index);
    });
    tdPlay.appendChild(playBtn);
    tr.appendChild(tdPlay);

    // Col 3: Title + Small Album Art Icon
    const tdTitle = document.createElement('td');
    tdTitle.className = 'col-title';
    const titleGroup = document.createElement('div');
    titleGroup.className = 'track-title-cell';
    
    if (track.album_art_url) {
      const img = document.createElement('img');
      img.className = 'track-album-art';
      img.src = track.album_art_url;
      img.alt = '';
      img.loading = 'lazy';
      titleGroup.appendChild(img);
    } else {
      const placeholder = document.createElement('div');
      placeholder.className = 'track-album-art-placeholder';
      placeholder.textContent = '🎵';
      titleGroup.appendChild(placeholder);
    }

    const textGrp = document.createElement('div');
    textGrp.className = 'track-text-group';
    textGrp.innerHTML = `<span class="track-title">${escapeHtml(track.title)}</span>`;
    titleGroup.appendChild(textGrp);
    tdTitle.appendChild(titleGroup);
    tr.appendChild(tdTitle);

    // Col 4: Artist
    const tdArtist = document.createElement('td');
    tdArtist.className = 'col-artist';
    const isGem = Boolean(track.is_hidden_gem || (track.popularity !== undefined && track.popularity > 0 && track.popularity <= 40));
    const gemIcon = isGem ? `<span class="hidden-gem-badge" style="font-size:8px; padding:0 3px; margin-left:4px;" title="Joya Oculta / Hidden Gem (${track.popularity ?? ''}%)">💎</span>` : '';
    tdArtist.innerHTML = `<span class="track-artist">${escapeHtml(track.artist)}</span>${gemIcon}`;
    tr.appendChild(tdArtist);

    // Col 5: Album
    const tdAlbum = document.createElement('td');
    tdAlbum.className = 'col-album';
    tdAlbum.innerHTML = `<span class="track-album">${escapeHtml(track.album)}</span>`;
    tr.appendChild(tdAlbum);

    // Col 6: Liked Songs Heart Toggle
    const tdLike = document.createElement('td');
    tdLike.className = 'col-like';
    const isLiked = (state.activeView === 'liked_songs') || Boolean(track.is_liked) || state.likedTrackIds.has(track.id);
    if (isLiked && track.id) {
      state.likedTrackIds.add(track.id);
    }
    const likeBtn = document.createElement('button');
    likeBtn.className = `track-like-btn ${isLiked ? 'liked' : ''}`;
    likeBtn.dataset.trackId = track.id;
    likeBtn.title = isLiked
      ? (state.currentLang === 'es' ? 'Quitar de Canciones que te gustan' : 'Remove from Liked Songs')
      : (state.currentLang === 'es' ? 'Guardar en Canciones que te gustan' : 'Save to Liked Songs');
    likeBtn.innerHTML = getHeartSvg(isLiked);
    likeBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      handleToggleLikeTrack(track);
    });
    tdLike.appendChild(likeBtn);
    tr.appendChild(tdLike);

    // Col 7: Duration
    const tdDur = document.createElement('td');
    tdDur.className = 'col-duration';
    const trackDur = track.duration_ms || track.durationMs || 0;
    tdDur.textContent = formatDuration(trackDur);
    tr.appendChild(tdDur);

    // Prevent native text selection on Shift/Cmd/Ctrl click
    tr.addEventListener('mousedown', (e) => {
      if (e.shiftKey || e.metaKey || e.ctrlKey) {
        e.preventDefault();
      }
    });

    // Click Handlers (Cmd+Click, Shift+Click, normal click saves anchor)
    tr.addEventListener('click', (e) => {
      if (e.target.closest('.row-play-btn') || e.target.closest('.track-like-btn') || e.target.type === 'checkbox') return;
      handleRowClick(e, track.id, index);
    });

    // Double click: Plays from this song
    tr.addEventListener('dblclick', () => {
      playFromIndex(index);
    });

    // Right-Click Context Menu for Track
    tr.addEventListener('contextmenu', (e) => {
      e.preventDefault();
      if (!state.selectedIds.has(track.id)) {
        state.selectedIds.clear();
        state.selectedIds.add(track.id);
        updateSelectionUI();
      }
      openContextMenu(e.clientX, e.clientY, index);
    });

    attachDragAndDropHandlers(tr, track.id, index);
    fragment.appendChild(tr);
  });

  DOM.tracksTbody.appendChild(fragment);
  updateSelectionUI();
  updateSaveAsPlaylistButtonState();
}

// --- Active List & Direct Track Playback ---
async function playTrackUris(uris, startingTitle = '', sourceName = '') {
  if (!uris || uris.length === 0) return;
  const isEs = state.currentLang === 'es';
  try {
    await api('/api/player/play', {
      method: 'POST',
      body: JSON.stringify({
        uris: uris,
        true_shuffle: false,
        device_id: DOM.deviceSelect?.value || null
      })
    });
    if (startingTitle) {
      if (sourceName) {
        showToast(isEs ? `▶ Reproduciendo "${startingTitle}" (${sourceName})` : `▶ Playing "${startingTitle}" (${sourceName})`);
      } else {
        showToast(isEs ? `▶ Reproduciendo "${startingTitle}"` : `▶ Playing "${startingTitle}"`);
      }
    } else {
      showToast(isEs ? `▶ Reproduciendo ${uris.length} canción${uris.length === 1 ? '' : 'es'}` : `▶ Playing ${uris.length} song${uris.length === 1 ? '' : 's'}`);
    }
    pollPlayerState();
  } catch (e) {
    showToast((isEs ? 'Error de reproducción: ' : 'Playback error: ') + e.message, 'error');
  }
}

async function handlePlayRightTrack(track, index, sourceListType) {
  if (!track) return;
  const isEs = state.currentLang === 'es';

  // If already playing this song, toggle play/pause
  const isCurrentPlaying = Boolean(
    state.currentPlayingTrackId && (track.id === state.currentPlayingTrackId || (track.uri && track.uri === state.currentPlayingTrackId))
  );

  if (isCurrentPlaying) {
    if (ytAudioPlayer && typeof ytAudioPlayer.getPlayerState === 'function') {
      const pState = ytAudioPlayer.getPlayerState();
      if (pState === 1) { // playing -> pause
        ytAudioPlayer.pauseVideo();
        updatePlayerPlayPauseButton(false);
        if (state.playerState) state.playerState.is_playing = false;
        syncPlayerStateToBackend();
      } else { // paused -> play
        ytAudioPlayer.playVideo();
        updatePlayerPlayPauseButton(true);
        if (state.playerState) state.playerState.is_playing = true;
        syncPlayerStateToBackend();
      }
      return;
    }
  }

  playYouTubeTrack(track);
  showToast(isEs ? `▶ Reproduciendo "${track.title}"` : `▶ Playing "${track.title}"`);

  try {
    await api('/api/player/play', {
      method: 'POST',
      body: JSON.stringify({
        uris: [track.uri || `yt:track:${track.id}`],
        track_id: track.id,
        true_shuffle: false
      })
    });
  } catch (e) {}
}

// --- Liked Songs Management ---
function getHeartSvg(isLiked) {
  if (isLiked) {
    return `<svg class="heart-icon liked" viewBox="0 0 24 24" width="15" height="15" fill="#1DB954" stroke="#1DB954" stroke-width="1"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>`;
  } else {
    return `<svg class="heart-icon" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>`;
  }
}

async function loadLikedTrackIds() {
  try {
    const res = await api('/api/tracks/liked-ids');
    const ids = Array.isArray(res) ? res : (res && Array.isArray(res.liked_ids) ? res.liked_ids : []);
    state.likedTrackIds = new Set(ids);
    const countLikedBadge = document.getElementById('count-liked');
    if (countLikedBadge) {
      countLikedBadge.textContent = ids.length;
    }
  } catch (e) {
    console.error('Failed to load liked track IDs:', e);
  }
}

async function handleToggleLikeTrack(track) {
  if (!track || !track.id) return;
  const isEs = state.currentLang === 'es';
  const currentlyLiked = state.likedTrackIds.has(track.id) || Boolean(track.is_liked);
  const nextLiked = !currentlyLiked;

  // 1. Optimistic UI update in state
  if (nextLiked) {
    state.likedTrackIds.add(track.id);
  } else {
    state.likedTrackIds.delete(track.id);
  }

  // 2. Update track object if in memory
  track.is_liked = nextLiked;

  // 3. Update all like buttons for this track across all views (queue + right panel)
  updateTrackLikeButtonsUI(track.id, nextLiked);

  // 4. Update sidebar badge count
  const countLikedBadge = document.getElementById('count-liked');
  if (countLikedBadge) {
    let currentCount = parseInt(countLikedBadge.textContent, 10) || 0;
    countLikedBadge.textContent = Math.max(0, currentCount + (nextLiked ? 1 : -1));
  }

  // 5. Toast notification
  if (nextLiked) {
    showToast(isEs ? `💚 Añadida a Canciones que te gustan` : `💚 Saved to Liked Songs`);
  } else {
    showToast(isEs ? `🤍 Quitada de Canciones que te gustan` : `🤍 Removed from Liked Songs`);
  }

  // 6. Send API request
  try {
    const res = await api('/api/tracks/like', {
      method: 'POST',
      body: JSON.stringify({
        track_id: track.id,
        liked: nextLiked,
        track: {
          id: track.id,
          uri: track.uri || `yt:track:${track.id}`,
          title: track.title,
          artist: track.artist,
          primary_artist: track.primary_artist || '',
          album: track.album || '',
          album_art_url: track.album_art_url || track.thumbnailUrl || '',
          thumbnailUrl: track.thumbnailUrl || track.album_art_url || '',
          duration_ms: track.duration_ms || track.durationMs || 0,
          durationMs: track.duration_ms || track.durationMs || 0,
          loudnessDb: track.loudnessDb || 0.0
        }
      })
    });
    await loadPlaylists();
    if (state.activeView === 'liked_songs') {
      await loadTracks();
    }
  } catch (err) {
    // Revert optimistic state on failure
    if (nextLiked) {
      state.likedTrackIds.delete(track.id);
    } else {
      state.likedTrackIds.add(track.id);
    }
    track.is_liked = currentlyLiked;
    updateTrackLikeButtonsUI(track.id, currentlyLiked);
    showToast((isEs ? 'Error al actualizar Canciones que te gustan: ' : 'Error updating Liked Songs: ') + err.message, 'error');
  }
}

function updateTrackLikeButtonsUI(trackId, isLiked) {
  const isEs = state.currentLang === 'es';
  const btns = document.querySelectorAll(`.track-like-btn[data-track-id="${trackId}"], .right-item-like-btn[data-track-id="${trackId}"]`);
  btns.forEach(btn => {
    btn.classList.toggle('liked', isLiked);
    btn.title = isLiked
      ? (isEs ? 'Quitar de Canciones que te gustan' : 'Remove from Liked Songs')
      : (isEs ? 'Guardar en Canciones que te gustan' : 'Save to Liked Songs');
    btn.innerHTML = getHeartSvg(isLiked);
  });
}

// --- Artist Blacklist (Surprise Me Exclusion) Management ---

async function loadBlacklist() {
  try {
    const data = await api('/api/blacklist/artists');
    state.blacklist = data.artists || [];
    renderBlacklistTable();
    updateCounts(state.blacklist.length);
  } catch (err) {
    showToast('Error loading blacklist: ' + err.message, 'error');
  }
}

function renderBlacklistTable() {
  if (!DOM.blacklistTableBody) return;
  DOM.blacklistTableBody.innerHTML = '';
  
  const query = (state.blacklistSearch || '').toLowerCase().trim();
  const list = state.blacklist.filter(item => !query || item.name.toLowerCase().includes(query));
  
  const tableEl = DOM.blacklistTableBody.closest('table');
  if (list.length === 0) {
    if (tableEl) tableEl.classList.add('hidden');
    DOM.blacklistEmptyState?.classList.remove('hidden');
    return;
  }
  
  if (tableEl) tableEl.classList.remove('hidden');
  DOM.blacklistEmptyState?.classList.add('hidden');
  
  const isEs = state.currentLang === 'es';
  
  list.forEach((item, index) => {
    const tr = document.createElement('tr');
    tr.dataset.artistName = item.name;
    
    let dateStr = item.created_at || '';
    if (dateStr) {
      try {
        const d = new Date(dateStr);
        if (!isNaN(d.getTime())) {
          dateStr = d.toLocaleDateString(isEs ? 'es-ES' : 'en-US', { month: 'short', day: 'numeric', year: 'numeric' });
        }
      } catch (e) {}
    }
    
    tr.innerHTML = `
      <td style="text-align: center; color: var(--text-muted); font-size: 11px;">${index + 1}</td>
      <td>
        <div class="blacklist-artist-name">
          <span style="font-size: 11px;">🎤</span>
          <span class="blacklist-artist-title">${escapeHtml(item.name)}</span>
        </div>
      </td>
      <td style="color: var(--text-muted); font-size: 11px;">${escapeHtml(dateStr || '-')}</td>
      <td style="text-align: right;">
        <button type="button" class="blacklist-unblock-btn" title="${isEs ? 'Desbloquear artista' : 'Unblock artist'}">
          ✕ ${isEs ? 'Quitar' : 'Unblock'}
        </button>
      </td>
    `;
    
    tr.querySelector('.blacklist-unblock-btn').addEventListener('click', async (e) => {
      e.stopPropagation();
      await removeArtistFromBlacklist(item.name);
    });
    
    DOM.blacklistTableBody.appendChild(tr);
  });
}

async function addArtistToBlacklist(name, externalId = null) {
  const cleanName = (name || '').trim();
  if (!cleanName) return;
  try {
    await api('/api/blacklist/artists', {
      method: 'POST',
      body: JSON.stringify({ name: cleanName, external_id: externalId })
    });
    const isEs = state.currentLang === 'es';
    showToast(isEs ? `🚫 "${cleanName}" añadido a la lista negra` : `🚫 "${cleanName}" added to Blacklist`);
    await refreshBlacklistCount();
    if (state.activeView === 'blacklist') {
      await loadBlacklist();
    }
  } catch (err) {
    showToast('Error adding artist to blacklist: ' + err.message, 'error');
  }
}

async function removeArtistFromBlacklist(nameOrId) {
  try {
    await api(`/api/blacklist/artists/${encodeURIComponent(nameOrId)}`, {
      method: 'DELETE'
    });
    const isEs = state.currentLang === 'es';
    showToast(isEs ? `✅ "${nameOrId}" eliminado de la lista negra` : `✅ "${nameOrId}" removed from Blacklist`);
    await refreshBlacklistCount();
    if (state.activeView === 'blacklist') {
      await loadBlacklist();
    }
  } catch (err) {
    showToast('Error removing artist from blacklist: ' + err.message, 'error');
  }
}

async function refreshBlacklistCount() {
  try {
    const data = await api('/api/blacklist/artists');
    const count = data.count !== undefined ? data.count : (data.artists ? data.artists.length : 0);
    const countEl = document.getElementById('count-blacklist');
    if (countEl) countEl.textContent = count;
  } catch (e) {}
}

function initBlacklistEvents() {
  if (DOM.blacklistAddInput && DOM.blacklistSuggestions) {
    setupDiscoveryTypeahead(DOM.blacklistAddInput, DOM.blacklistSuggestions, 'artist', async (item) => {
      if (item && item.name) {
        await addArtistToBlacklist(item.name, item.id);
        DOM.blacklistAddInput.value = '';
        DOM.blacklistSuggestions.classList.add('hidden');
      }
    });
  }

  DOM.blacklistAddBtn?.addEventListener('click', async () => {
    const val = DOM.blacklistAddInput?.value.trim();
    if (val) {
      await addArtistToBlacklist(val);
      DOM.blacklistAddInput.value = '';
      DOM.blacklistSuggestions?.classList.add('hidden');
    }
  });

  DOM.blacklistAddInput?.addEventListener('keydown', async (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      const val = DOM.blacklistAddInput.value.trim();
      if (val) {
        await addArtistToBlacklist(val);
        DOM.blacklistAddInput.value = '';
        DOM.blacklistSuggestions?.classList.add('hidden');
      }
    }
  });

  DOM.blacklistSearchFilter?.addEventListener('input', (e) => {
    state.blacklistSearch = e.target.value;
    renderBlacklistTable();
  });

  DOM.blacklistContainer?.addEventListener('dragover', (e) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'copy';
    DOM.blacklistContainer.classList.add('drop-target-active');
  });
  DOM.blacklistContainer?.addEventListener('dragleave', () => {
    DOM.blacklistContainer.classList.remove('drop-target-active');
  });
  DOM.blacklistContainer?.addEventListener('drop', async (e) => {
    e.preventDefault();
    e.stopPropagation();
    DOM.blacklistContainer.classList.remove('drop-target-active');

    let payload = null;
    try {
      const raw = e.dataTransfer.getData('application/json') || e.dataTransfer.getData('text/plain');
      payload = raw ? JSON.parse(raw) : null;
    } catch (err) {}
    if (!payload && window.__draggedTracksFromRight) {
      payload = { source: 'right', tracks: window.__draggedTracksFromRight };
    }
    window.__draggedTracksFromRight = null;
    if (payload && payload.tracks && payload.tracks.length > 0) {
      await handleDropTracksOntoBlacklist(payload.tracks);
    }
  });
}

async function handleDropTracksOntoBlacklist(tracks) {
  if (!tracks || tracks.length === 0) return;
  const isEs = state.currentLang === 'es';
  const blockedArtists = new Set();

  for (const track of tracks) {
    const mainArtist = track.primary_artist || (track.artist ? track.artist.split(',')[0].split(' feat.')[0].split(' ft.')[0].trim() : '');
    if (mainArtist && mainArtist !== 'Unknown Artist' && mainArtist !== 'Unknown') {
      blockedArtists.add(mainArtist);
      await addArtistToBlacklist(mainArtist);
    }
  }

  if (blockedArtists.size > 0) {
    // Purge from active discovery
    state.discovery.discoveredTracks = (state.discovery.discoveredTracks || []).filter(t => {
      const ma = t.primary_artist || (t.artist ? t.artist.split(',')[0].split(' feat.')[0].split(' ft.')[0].trim() : '');
      return !blockedArtists.has(ma);
    });
    state.rightTracks = [...state.discovery.discoveredTracks];
    if (DOM.discoveryResultCountBadge) {
      DOM.discoveryResultCountBadge.textContent = t('discoveredCountBadge', { count: state.discovery.discoveredTracks.length });
    }
    renderDiscoveryResultsItems(state.discovery.discoveredTracks);

    if (state.activeView === 'blacklist') {
      await loadBlacklist();
    }
    showToast(isEs
      ? `🚫 ${blockedArtists.size} artista(s) añadido(s) a la lista negra`
      : `🚫 Added ${blockedArtists.size} artist(s) to Blacklist`);
  }
}

async function playFromIndex(startIndex) {
  if (startIndex < 0 || startIndex >= state.tracks.length) return;
  const startingTrack = state.tracks[startIndex];
  playYouTubeTrack(startingTrack);
  showToast(`▶ Playing "${startingTrack.title}" & queuing next songs`);

  const queueUris = state.tracks.slice(startIndex).map(t => t.uri || `yt:track:${t.id}`);
  try {
    await api('/api/player/play', {
      method: 'POST',
      body: JSON.stringify({
        uris: queueUris,
        track_id: startingTrack.id,
        true_shuffle: false,
        device_id: DOM.deviceSelect?.value || null
      })
    });
  } catch (e) {}
}

async function playNextInActiveList() {
  if (state.tracks.length === 0) return;
  let currIdx = -1;
  if (state.currentPlayingTrackId) {
    currIdx = state.tracks.findIndex(t => t.id === state.currentPlayingTrackId);
  }
  if (currIdx === -1 && state.currentPlayingTrackTitle) {
    currIdx = state.tracks.findIndex(t => t.title.toLowerCase().trim() === state.currentPlayingTrackTitle.toLowerCase().trim());
  }
  const nextIdx = currIdx + 1;
  if (nextIdx < state.tracks.length) {
    playFromIndex(nextIdx);
  } else {
    playFromIndex(0);
  }
}

async function playPrevInActiveList() {
  if (state.tracks.length === 0) return;
  let currIdx = 0;
  if (state.currentPlayingTrackId) {
    currIdx = state.tracks.findIndex(t => t.id === state.currentPlayingTrackId);
  }
  if (currIdx === -1 && state.currentPlayingTrackTitle) {
    currIdx = state.tracks.findIndex(t => t.title.toLowerCase().trim() === state.currentPlayingTrackTitle.toLowerCase().trim());
  }
  if (currIdx === -1) currIdx = 0;
  const prevIdx = Math.max(0, currIdx - 1);
  playFromIndex(prevIdx);
}

// --- True Mathematical Shuffle (Always generates a fresh random order!) ---
function toggleTrueShuffle() {
  if (state.isOrderLocked) {
    showToast('🔒 Active list order is locked. Unlock it to shuffle!', 'error');
    return;
  }

  state.isTrueShuffleActive = true;
  DOM.shuffleActiveListBtn?.classList.add('active');
  DOM.ctrlTrueShuffle?.classList.add('active');

  if (state.tracks.length === 0) {
    showToast('No tracks in active list to shuffle', 'error');
    return;
  }

  // Find currently playing track
  let currentIdx = -1;
  if (state.currentPlayingTrackTitle) {
    currentIdx = state.tracks.findIndex(t => t.title.toLowerCase().trim() === state.currentPlayingTrackTitle.toLowerCase().trim());
  }

  const tracksToShuffle = (currentIdx !== -1)
    ? state.tracks.filter((_, idx) => idx !== currentIdx)
    : [...state.tracks];

  // Mathematical Anti-Clumping Shuffle
  const shuffled = antiClumpShuffle(tracksToShuffle);

  if (currentIdx !== -1) {
    const currentTrack = state.tracks[currentIdx];
    state.tracks = [currentTrack, ...shuffled];
  } else {
    state.tracks = shuffled;
  }

  renderTracksTable();
  showToast('🔀 Shuffled! New random order generated');
}

function antiClumpShuffle(arr) {
  const result = [...arr];
  for (let i = result.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [result[i], result[j]] = [result[j], result[i]];
  }
  // Anti-consecutive artist smoothing
  for (let i = 0; i < result.length - 1; i++) {
    if (result[i].artist && result[i].artist === result[i + 1].artist) {
      for (let k = i + 2; k < result.length; k++) {
        if (result[k].artist !== result[i].artist) {
          [result[i + 1], result[k]] = [result[k], result[i + 1]];
          break;
        }
      }
    }
  }
  return result;
}

// --- Selection Engine (Main Panel) ---
function handleRowClick(e, trackId, index) {
  if (e.metaKey || e.ctrlKey || e.shiftKey) {
    window.getSelection()?.removeAllRanges();
  }
  if (e.metaKey || e.ctrlKey) {
    if (state.selectedIds.has(trackId)) {
      state.selectedIds.delete(trackId);
    } else {
      state.selectedIds.add(trackId);
    }
    state.lastSelectedId = trackId;
    state.lastClickedIndex = index;
    updateSelectionUI();
  } else if (e.shiftKey) {
    const start = Math.min(state.lastClickedIndex, index);
    const end = Math.max(state.lastClickedIndex, index);
    for (let i = start; i <= end; i++) {
      state.selectedIds.add(state.tracks[i].id);
    }
    state.lastSelectedId = trackId;
    updateSelectionUI();
  } else {
    // Normal click: select and highlight this row
    state.selectedIds.clear();
    state.selectedIds.add(trackId);
    state.lastClickedIndex = index;
    state.lastSelectedId = trackId;
    updateSelectionUI();
  }
}

function toggleTrackSelection(trackId, isShift, index) {
  if (state.selectedIds.has(trackId)) {
    state.selectedIds.delete(trackId);
  } else {
    state.selectedIds.add(trackId);
  }
  state.lastSelectedId = trackId;
  state.lastClickedIndex = index;
  updateSelectionUI();
}

function updateSelectionUI() {
  const rows = DOM.tracksTbody.querySelectorAll('.track-row');
  rows.forEach(row => {
    const id = row.dataset.trackId;
    const isSel = state.selectedIds.has(id);
    row.classList.toggle('selected', isSel);
    const chk = row.querySelector('.col-check input');
    if (chk) chk.checked = isSel;
  });

  const count = state.selectedIds.size;
  // Per user requirement: do not pop up options bar/modal when selecting tracks in main queue
  DOM.selectionActionBar?.classList.add('hidden');

  if (DOM.selectAllCheckbox) {
    DOM.selectAllCheckbox.checked = count > 0 && count === state.tracks.length;
  }
}

// --- Drag & Drop (Supports Reordering Main List + Dropping from Right Panel!) ---
let draggedTrackData = [];

function attachDragAndDropHandlers(tr, trackId, index) {
  tr.addEventListener('dragstart', (e) => {
    if (state.isOrderLocked) {
      e.preventDefault();
      showToast('🔒 Active list order is locked. Unlock it to reorder!', 'error');
      return;
    }
    let movedTracks = [];
    if (state.selectedIds.has(trackId)) {
      movedTracks = state.tracks.filter(t => state.selectedIds.has(t.id));
    } else {
      const single = state.tracks.find(t => t.id === trackId);
      if (single) movedTracks = [single];
    }
    draggedTrackData = movedTracks;
    tr.classList.add('dragging');
    e.dataTransfer.effectAllowed = 'copyMove';
    e.dataTransfer.setData('application/json', JSON.stringify({ source: 'main', tracks: movedTracks }));
  });

  tr.addEventListener('dragend', () => {
    tr.classList.remove('dragging');
    document.querySelectorAll('.track-row').forEach(r => {
      r.classList.remove('drop-target-above', 'drop-target-below');
    });
  });

  tr.addEventListener('dragover', (e) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'copy';

    const rect = tr.getBoundingClientRect();
    const relY = e.clientY - rect.top;
    if (relY < rect.height / 2) {
      tr.classList.add('drop-target-above');
      tr.classList.remove('drop-target-below');
    } else {
      tr.classList.add('drop-target-below');
      tr.classList.remove('drop-target-above');
    }

    handleDragAutoScroll(e.clientY);
  });

  tr.addEventListener('dragleave', () => {
    tr.classList.remove('drop-target-above', 'drop-target-below');
  });

  tr.addEventListener('drop', async (e) => {
    e.preventDefault();
    e.stopPropagation();

    const rect = tr.getBoundingClientRect();
    const dropPosition = (e.clientY - rect.top < rect.height / 2) ? 'above' : 'below';
    tr.classList.remove('drop-target-above', 'drop-target-below');

    const targetTrackId = tr.dataset.trackId || (state.tracks[index] ? state.tracks[index].id : null);

    try {
      let payload = null;
      try {
        const raw = e.dataTransfer.getData('application/json') || e.dataTransfer.getData('text/plain');
        payload = raw ? JSON.parse(raw) : null;
      } catch (err) {}

      if (!payload && window.__draggedTracksFromRight) {
        payload = { source: 'right', tracks: window.__draggedTracksFromRight };
      }
      window.__draggedTracksFromRight = null;

      // Case 1: Songs dropped from right browser / Discovery studio
      if (payload && payload.source === 'right' && payload.tracks && payload.tracks.length > 0) {
        const insertIdx = dropPosition === 'below' ? index + 1 : index;
        insertTracksIntoMainPanel(payload.tracks, insertIdx);
        return;
      }

      // Case 2: Reordering main list
      if (payload && payload.tracks && payload.source === 'main') {
        const movedIds = payload.tracks.map(t => t.id);
        executeReorder(movedIds, targetTrackId, dropPosition);
      } else if (draggedTrackData.length > 0) {
        executeReorder(draggedTrackData.map(t => t.id), targetTrackId, dropPosition);
      }
    } catch (err) {
      if (draggedTrackData.length > 0) {
        executeReorder(draggedTrackData.map(t => t.id), targetTrackId, dropPosition);
      }
    }
  });
}

function initWorkspaceContainerDrop() {
  const container = DOM.tableContainer || document.getElementById('main-content');
  if (!container) return;

  container.addEventListener('dragover', (e) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'copy';
  });

  container.addEventListener('drop', async (e) => {
    if (e.target.closest('.track-row')) return; // Row drop handled by row listener
    e.preventDefault();
    e.stopPropagation();

    let payload = null;
    try {
      const raw = e.dataTransfer.getData('application/json') || e.dataTransfer.getData('text/plain');
      payload = raw ? JSON.parse(raw) : null;
    } catch (err) {}

    if (!payload && window.__draggedTracksFromRight) {
      payload = { source: 'right', tracks: window.__draggedTracksFromRight };
    }
    window.__draggedTracksFromRight = null;

    if (!payload || !payload.tracks || payload.tracks.length === 0) return;

    if (state.activeView === 'blacklist') {
      await handleDropTracksOntoBlacklist(payload.tracks);
      return;
    }

    if (payload.source === 'right') {
      insertTracksIntoMainPanel(payload.tracks, state.tracks.length);
    }
  });
}

function handleDragAutoScroll(clientY) {
  const container = DOM.tableContainer;
  const rect = container.getBoundingClientRect();
  const topEdge = rect.top;
  const bottomEdge = rect.bottom;
  const threshold = 60;

  if (clientY < topEdge + threshold) {
    const speed = Math.max(2, Math.round(((topEdge + threshold - clientY) / threshold) * 16));
    container.scrollTop -= speed;
  } else if (clientY > bottomEdge - threshold) {
    const speed = Math.max(2, Math.round(((clientY - (bottomEdge - threshold)) / threshold) * 16));
    container.scrollTop += speed;
  }
}

async function executeReorder(movedIds, targetTrackId, dropPosition = 'above') {
  if (state.isOrderLocked) {
    showToast('🔒 Active list order is locked. Unlock it to reorder!', 'error');
    return;
  }
  if (!movedIds || movedIds.length === 0) return;

  let actualTargetId = targetTrackId;
  if (typeof targetTrackId === 'number') {
    actualTargetId = state.tracks[targetTrackId]?.id || null;
  }

  // 4. If dropping onto an item in the selection itself, treat as a no-op.
  if (actualTargetId && movedIds.includes(actualTargetId)) {
    return;
  }

  const movedTracks = state.tracks.filter(t => movedIds.includes(t.id));
  if (movedTracks.length === 0) return;

  // Remaining array after removing moved items
  const remaining = state.tracks.filter(t => !movedIds.includes(t.id));

  // 1. In remaining array, look up target item's new index directly
  let insertIdx;
  if (actualTargetId) {
    const targetIndexInRemaining = remaining.findIndex(t => t.id === actualTargetId);
    if (targetIndexInRemaining === -1) {
      insertIdx = remaining.length;
    } else {
      // 2. If dropping above the row, insert at targetIndexInRemaining.
      // 3. If dropping below the row, insert at targetIndexInRemaining + 1.
      insertIdx = dropPosition === 'below' ? targetIndexInRemaining + 1 : targetIndexInRemaining;
    }
  } else {
    insertIdx = remaining.length;
  }

  state.undoStack.push([...state.tracks]);
  remaining.splice(insertIdx, 0, ...movedTracks);
  state.tracks = remaining;

  state.userCustomOrderIds = state.tracks.map(t => t.id);
  renderTracksTable();

  // Persist reorder to DB if editing a playlist
  if (state.activeView && state.activeView !== 'all' && state.activeView !== 'blacklist') {
    try {
      await api('/api/playlists/reorder', {
        method: 'POST',
        body: JSON.stringify({
          playlist_id: state.activeView,
          track_ids: state.userCustomOrderIds
        })
      });
      await loadPlaylists();
    } catch (e) {}
  }
}

async function insertTracksIntoMainPanel(newTracks, targetIndex) {
  if (!newTracks || newTracks.length === 0) return;
  state.undoStack.push([...state.tracks]);

  const insertIdx = (targetIndex !== undefined && targetIndex !== null) ? targetIndex : state.tracks.length;
  state.tracks.splice(insertIdx, 0, ...newTracks);
  state.userCustomOrderIds = state.tracks.map(t => t.id);
  renderTracksTable();

  // Persist to DB if editing a playlist (liked_songs, local_pl_...)
  if (state.activeView && state.activeView !== 'all' && state.activeView !== 'blacklist') {
    for (const t of newTracks) {
      try {
        await api(`/api/playlists/${state.activeView}/add-track`, {
          method: 'POST',
          body: JSON.stringify({
            track_id: t.id,
            track: {
              id: t.id,
              uri: t.uri || `yt:track:${t.id}`,
              title: t.title,
              artist: t.artist,
              primary_artist: t.primary_artist || '',
              album: t.album || '',
              duration_ms: t.duration_ms || t.durationMs || 0,
              durationMs: t.duration_ms || t.durationMs || 0,
              thumbnailUrl: t.thumbnailUrl || t.album_art_url || '',
              album_art_url: t.album_art_url || t.thumbnailUrl || '',
              loudnessDb: t.loudnessDb || 0.0
            }
          })
        });
      } catch (e) {}
    }
    if (state.activeView === 'liked_songs') {
      newTracks.forEach(t => state.likedTrackIds.add(t.id));
      const countLiked = document.getElementById('count-liked');
      if (countLiked) countLiked.textContent = state.tracks.length;
    }
    await loadPlaylists();
  }

  // Sync to backend queue
  api('/api/queue/append', {
    method: 'POST',
    body: JSON.stringify({
      tracks: newTracks.map(t => ({
        id: t.id,
        title: t.title,
        artist: t.artist,
        album: t.album || '',
        durationMs: t.duration_ms || t.durationMs || 0,
        thumbnailUrl: t.album_art_url || t.thumbnailUrl || ''
      }))
    })
  }).catch(() => {});

  showToast(`➕ Added ${newTracks.length} song${newTracks.length === 1 ? '' : 's'} to workspace queue`);
}

async function handleRemoveSelectedFromList() {
  if (!state.selectedIds || state.selectedIds.size === 0) {
    showToast('Select songs in the main list first', 'error');
    return;
  }

  const removedCount = state.selectedIds.size;
  // Save to undo stack for Cmd+Z recovery
  state.undoStack.push([...state.tracks]);

  // Filter active queue in workspace
  state.tracks = state.tracks.filter(t => !state.selectedIds.has(t.id));
  state.userCustomOrderIds = state.tracks.map(t => t.id);
  state.selectedIds.clear();

  // Persist to DB if editing a playlist
  if (state.activeView && state.activeView !== 'all' && state.activeView !== 'blacklist') {
    try {
      await api('/api/playlists/reorder', {
        method: 'POST',
        body: JSON.stringify({
          playlist_id: state.activeView,
          track_ids: state.userCustomOrderIds
        })
      });
      if (state.activeView === 'liked_songs') {
        const countLiked = document.getElementById('count-liked');
        if (countLiked) countLiked.textContent = state.tracks.length;
      }
      await loadPlaylists();
    } catch (e) {}
  }

  renderTracksTable();
  updateSelectionUI();
  showToast(t('toastRemovedFromList', { count: removedCount, s: removedCount === 1 ? '' : (state.currentLang === 'es' ? 'es' : 's') }));
}

async function handleKeepOnlySelectedInList() {
  if (!state.selectedIds || state.selectedIds.size === 0) {
    showToast('Select songs in the main list first', 'error');
    return;
  }

  const keptCount = state.selectedIds.size;
  const removedCount = state.tracks.length - keptCount;
  if (removedCount === 0) {
    showToast('All songs in the list are already selected');
    return;
  }

  // Save to undo stack for Cmd+Z recovery
  state.undoStack.push([...state.tracks]);

  // Keep only selected in workspace
  state.tracks = state.tracks.filter(t => state.selectedIds.has(t.id));
  state.userCustomOrderIds = state.tracks.map(t => t.id);
  state.selectedIds.clear();

  // Persist to DB if editing a playlist
  if (state.activeView && state.activeView !== 'all' && state.activeView !== 'blacklist') {
    try {
      await api('/api/playlists/reorder', {
        method: 'POST',
        body: JSON.stringify({
          playlist_id: state.activeView,
          track_ids: state.userCustomOrderIds
        })
      });
      if (state.activeView === 'liked_songs') {
        const countLiked = document.getElementById('count-liked');
        if (countLiked) countLiked.textContent = state.tracks.length;
      }
      await loadPlaylists();
    } catch (e) {}
  }

  renderTracksTable();
  updateSelectionUI();
  showToast(t('toastKeptOnlySelected', { kept: keptCount, removed: removedCount }));
}

// --- User Order vs Column Sorting ---
function applyColumnSort(column) {
  let direction = 'asc';
  if (state.sortState.column === column && state.sortState.direction === 'asc') {
    direction = 'desc';
  }

  state.sortState = { column, direction, isTemporary: true };

  state.tracks.sort((a, b) => {
    let valA = a[column];
    let valB = b[column];
    if (typeof valA === 'string') valA = valA.toLowerCase();
    if (typeof valB === 'string') valB = valB.toLowerCase();
    if (valA < valB) return direction === 'asc' ? -1 : 1;
    if (valA > valB) return direction === 'asc' ? 1 : -1;
    return 0;
  });

  DOM.resetOrderBtn.classList.remove('hidden');
  DOM.saveOrderBtn.classList.remove('hidden');
  renderTracksTable();
}

function resetToUserOrder() {
  state.sortState = { column: 'order_index', direction: 'asc', isTemporary: false };
  DOM.resetOrderBtn.classList.add('hidden');
  DOM.saveOrderBtn.classList.add('hidden');
  loadTracks();
}

async function saveAsUserOrder() {
  state.userCustomOrderIds = state.tracks.map(t => t.id);
  state.sortState.isTemporary = false;
  DOM.resetOrderBtn.classList.add('hidden');
  DOM.saveOrderBtn.classList.add('hidden');

  if (state.activeView && state.activeView.startsWith('custom_')) {
    await api('/api/playlists/reorder', {
      method: 'POST',
      body: JSON.stringify({
        playlist_id: state.activeView,
        track_ids: state.userCustomOrderIds
      })
    });
    showToast('💾 Saved sequence to playlist');
  } else {
    showToast('💾 Workspace sequence updated. Click "💾 Save as Playlist" to create a playlist.');
  }
}

// --- Right Panel: Dual Source Browser & YouTube Music Search ---
let rightSearchTimeout = null;

function initRightPanel() {
  if (!DOM.tabRightSearch || !DOM.tabRightPlaylist) return;

  DOM.tabRightSearch.addEventListener('click', () => {
    state.rightTab = 'search';
    DOM.tabRightSearch.classList.add('active');
    DOM.tabRightPlaylist.classList.remove('active');
    DOM.tabRightDiscovery?.classList.remove('active');
    DOM.rightSearchControls.classList.remove('hidden');
    DOM.rightPlaylistControls.classList.add('hidden');
    DOM.rightDiscoveryControls?.classList.add('hidden');
    DOM.rightStandardActionHeader?.classList.remove('hidden');
    DOM.rightItemsContainer?.classList.remove('hidden');
    DOM.rightSearchInput.focus();
  });

  DOM.tabRightPlaylist.addEventListener('click', () => {
    state.rightTab = 'playlist';
    DOM.tabRightPlaylist.classList.add('active');
    DOM.tabRightSearch.classList.remove('active');
    DOM.tabRightDiscovery?.classList.remove('active');
    DOM.rightPlaylistControls.classList.remove('hidden');
    DOM.rightSearchControls.classList.add('hidden');
    DOM.rightDiscoveryControls?.classList.add('hidden');
    DOM.rightStandardActionHeader?.classList.remove('hidden');
    DOM.rightItemsContainer?.classList.remove('hidden');
    if (DOM.rightPlaylistPicker.value) {
      loadRightPlaylistTracks(DOM.rightPlaylistPicker.value);
    }
  });

  DOM.tabRightDiscovery?.addEventListener('click', () => {
    state.rightTab = 'discovery';
    DOM.tabRightDiscovery.classList.add('active');
    DOM.tabRightSearch.classList.remove('active');
    DOM.tabRightPlaylist.classList.remove('active');
    DOM.rightDiscoveryControls?.classList.remove('hidden');
    DOM.rightSearchControls.classList.add('hidden');
    DOM.rightPlaylistControls.classList.add('hidden');
    DOM.rightStandardActionHeader?.classList.add('hidden');
    DOM.rightItemsContainer?.classList.add('hidden');

    if (state.discovery.discoveredTracks.length > 0) {
      DOM.discoveryResultsSection?.classList.remove('hidden');
      renderDiscoveryResultsItems(state.discovery.discoveredTracks);
    }
  });

  // Search Modifiers Pills
  DOM.searchModPills?.forEach(pill => {
    pill.addEventListener('click', () => {
      DOM.searchModPills.forEach(p => p.classList.remove('active'));
      pill.classList.add('active');
      state.searchModifier = pill.dataset.mod || 'all';
      if (DOM.rightSearchInput.value.trim()) {
        executeRightSearch(DOM.rightSearchInput.value.trim());
      }
    });
  });

  // Live Suggestion Typeahead for Right Search Input
  if (DOM.rightSearchInput && DOM.rightSearchSuggestions) {
    setupDiscoveryTypeahead(
      DOM.rightSearchInput,
      DOM.rightSearchSuggestions,
      () => state.searchModifier === 'artist' ? 'artist' : (state.searchModifier === 'track' ? 'track' : 'all'),
      (item) => {
        const val = item.name || item.title || '';
        DOM.rightSearchInput.value = val;
        DOM.rightSearchSuggestions.classList.add('hidden');
        if (item.type === 'artist') {
          state.searchModifier = 'artist';
          DOM.searchModPills?.forEach(p => p.classList.toggle('active', p.dataset.mod === 'artist'));
        }
        if (val) executeRightSearch(val);
      }
    );
  }

  // Search Submit button & Enter key
  DOM.rightSearchSubmitBtn?.addEventListener('click', () => {
    DOM.rightSearchSuggestions?.classList.add('hidden');
    const q = DOM.rightSearchInput.value.trim();
    if (q) executeRightSearch(q);
  });

  DOM.rightSearchInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      DOM.rightSearchSuggestions?.classList.add('hidden');
      const q = DOM.rightSearchInput.value.trim();
      if (q) executeRightSearch(q);
    }
  });

  DOM.rightSearchInput.addEventListener('input', (e) => {
    const q = e.target.value.trim();
    if (rightSearchTimeout) clearTimeout(rightSearchTimeout);
    if (!q) {
      DOM.rightSearchSuggestions?.classList.add('hidden');
      DOM.rightItemsContainer.innerHTML = '<div class="search-placeholder-text">Type above to search YouTube Music.</div>';
      return;
    }
    rightSearchTimeout = setTimeout(() => executeRightSearch(q), 350);
  });

  // Right Playlist dropdown
  DOM.rightPlaylistPicker?.addEventListener('change', (e) => {
    const pId = e.target.value;
    if (pId) {
      loadRightPlaylistTracks(pId);
    } else {
      DOM.rightItemsContainer.innerHTML = '<div class="search-placeholder-text">Choose a playlist to browse.</div>';
    }
  });

  DOM.rightAddAllBtn?.addEventListener('click', () => {
    if (state.rightTracks && state.rightTracks.length > 0) {
      insertTracksIntoMainPanel(state.rightTracks, state.tracks.length);
      state.rightSelectedIds.clear();
      updateRightSelectionUI();
      showToast(`➕ Added all ${state.rightTracks.length} tracks to queue`);
    } else {
      showToast('No search results to add', 'error');
    }
  });

  DOM.rightAddSelectedBtn?.addEventListener('click', () => {
    const selectedTracks = state.rightTracks.filter(t => state.rightSelectedIds.has(t.id));
    if (selectedTracks.length > 0) {
      insertTracksIntoMainPanel(selectedTracks, state.tracks.length);
      state.rightSelectedIds.clear();
      updateRightSelectionUI();
    } else {
      showToast('Select songs in the right panel first', 'error');
    }
  });

  // Initialize the Discovery Studio
  initDiscoveryPanel();
}

async function executeRightSearch(query) {
  DOM.rightItemsContainer.innerHTML = '<div class="search-placeholder-text">🔍 Searching YouTube Music...</div>';
  try {
    const endpoint = `/api/search?q=${encodeURIComponent(query)}&type=${state.searchModifier}`;
    const data = await api(endpoint);
    let results = data.results || [];
    
    // Accent-insensitive and case-insensitive hard filtering
    const qNorm = normalizeStr(query);
    const queryWords = qNorm.split(/\s+/).filter(w => w.length > 0);

    if (state.searchModifier === 'track' || state.searchModifier === 'song') {
      // Hard filter: MUST be a song whose title contains the query or words (NOT songs sung by an artist with that name)
      results = results.filter(t => {
        const titleNorm = normalizeStr(t.title);
        return titleNorm.includes(qNorm) || 
          (queryWords.length > 0 && queryWords.every(w => titleNorm.includes(w))) ||
          (queryWords.length > 1 && queryWords.some(w => w.length >= 3 && titleNorm.includes(w)));
      });
    } else if (state.searchModifier === 'artist') {
      // Hard filter: song's artist must match the query (e.g. 'Cristián Castro' matches 'cristian castro')
      results = results.filter(t => {
        const artistNorm = normalizeStr(t.artist);
        return artistNorm.includes(qNorm) || 
          (queryWords.length > 0 && queryWords.every(w => artistNorm.includes(w)));
      });
    }

    state.rightTracks = results;
    renderRightItems();
  } catch (e) {
    console.error('Search error:', e);
    DOM.rightItemsContainer.innerHTML = `<div class="search-placeholder-text">Search failed: ${escapeHtml(e.message || String(e))}</div>`;
  }
}

async function loadRightPlaylistTracks(playlistId) {
  DOM.rightItemsContainer.innerHTML = '<div class="search-placeholder-text">Loading playlist songs...</div>';
  try {
    const data = await api(`/api/tracks?playlist_id=${playlistId}`);
    state.rightTracks = data.tracks || [];
    renderRightItems();
  } catch (e) {
    DOM.rightItemsContainer.innerHTML = '<div class="search-placeholder-text">Error loading playlist.</div>';
  }
}

function renderRightItems() {
  DOM.rightItemsContainer.innerHTML = '';
  state.rightSelectedIds.clear();
  updateRightSelectionUI();

  if (state.rightTracks.length === 0) {
    DOM.rightItemsContainer.innerHTML = '<div class="search-placeholder-text">No tracks found.</div>';
    return;
  }

  state.rightTracks.forEach((track, index) => {
    const row = document.createElement('div');
    row.className = 'right-item-row';
    row.dataset.trackId = track.id;
    row.dataset.trackUri = track.uri || (track.id ? `yt:track:${track.id}` : '');
    row.dataset.trackTitle = track.title || '';
    row.dataset.index = index;
    row.draggable = true;

    const isThisPlaying = Boolean(
      state.currentPlayingTrackId && (track.id === state.currentPlayingTrackId || (track.uri && track.uri === state.currentPlayingTrackId))
    );
    if (isThisPlaying) {
      row.classList.add('now-playing-row');
    }

    // Small Album Art
    const imgHtml = track.album_art_url
      ? `<img class="track-album-art" style="width:24px;height:24px;" src="${track.album_art_url}" alt="" loading="lazy">`
      : `<div class="track-album-art-placeholder" style="width:24px;height:24px;font-size:10px;">🎵</div>`;

    const playBtnTitle = isThisPlaying
      ? (state.currentLang === 'es' ? 'Pausar/Reanudar reproducción' : 'Pause/Resume playback')
      : (state.currentLang === 'es' ? 'Reproducir canción' : 'Play track');

    const isLiked = state.likedTrackIds.has(track.id) || Boolean(track.is_liked);
    const likeBtnTitle = isLiked
      ? (state.currentLang === 'es' ? 'Quitar de Canciones que te gustan' : 'Remove from Liked Songs')
      : (state.currentLang === 'es' ? 'Guardar en Canciones que te gustan' : 'Save to Liked Songs');
    const addBtnTitle = state.currentLang === 'es' ? 'Añadir a lista principal' : 'Add to main workspace';

    row.innerHTML = `
      <button class="right-item-play-btn ${isThisPlaying ? 'playing' : ''}" title="${escapeHtml(playBtnTitle)}" data-tooltip-title="${isThisPlaying ? t('tipPauseTrackTitle') : t('tipPlayTrackTitle')}" data-tooltip="${isThisPlaying ? t('tipPauseTrack') : t('tipPlayTrack')}">
        ${isThisPlaying ? '🔊' : '▶'}
      </button>
      ${imgHtml}
      <div class="right-item-meta">
        <div class="right-item-title">${escapeHtml(track.title)}</div>
        <div class="right-item-subtitle-row">
          <span class="right-item-artist">${escapeHtml(track.artist)}</span>
        </div>
      </div>
      <div class="right-item-actions">
        <button class="right-item-like-btn ${isLiked ? 'liked' : ''}" data-track-id="${track.id}" title="${escapeHtml(likeBtnTitle)}">
          ${getHeartSvg(isLiked)}
        </button>
        <span class="right-item-dur">${formatDuration(track.duration_ms)}</span>
        <button class="right-item-add-btn" title="${escapeHtml(addBtnTitle)}">+ Add</button>
      </div>
    `;

    row.addEventListener('mousedown', (e) => {
      if (e.shiftKey || e.metaKey || e.ctrlKey) {
        e.preventDefault();
      }
    });

    row.addEventListener('click', (e) => {
      if (e.target.closest('.right-item-add-btn') || e.target.closest('.right-item-play-btn') || e.target.closest('.right-item-like-btn') || e.target.closest('.track-album-art') || e.target.closest('.track-album-art-placeholder')) return;
      handleRightRowClick(e, track.id, index);
    });

    row.querySelector('.right-item-play-btn')?.addEventListener('click', (e) => {
      e.stopPropagation();
      handlePlayRightTrack(track, index, 'rightTracks');
    });

    row.querySelector('.right-item-like-btn')?.addEventListener('click', (e) => {
      e.stopPropagation();
      handleToggleLikeTrack(track);
    });

    const albumArtEl = row.querySelector('.track-album-art, .track-album-art-placeholder');
    if (albumArtEl) {
      albumArtEl.title = state.currentLang === 'es' ? 'Reproducir canción' : 'Play track';
      albumArtEl.addEventListener('click', (e) => {
        e.stopPropagation();
        handlePlayRightTrack(track, index, 'rightTracks');
      });
    }

    row.addEventListener('dblclick', () => {
      handlePlayRightTrack(track, index, 'rightTracks');
    });

    row.querySelector('.right-item-add-btn')?.addEventListener('click', (e) => {
      e.stopPropagation();
      insertTracksIntoMainPanel([track], state.tracks.length);
    });

    row.addEventListener('dragstart', (e) => {
      let moved = [];
      if (state.rightSelectedIds.has(track.id)) {
        moved = state.rightTracks.filter(t => state.rightSelectedIds.has(t.id));
      } else {
        moved = [track];
        state.rightSelectedIds.clear();
        state.rightSelectedIds.add(track.id);
        updateRightSelectionUI();
      }
      window.__draggedTracksFromRight = moved;
      row.classList.add('dragging');
      e.dataTransfer.effectAllowed = 'copy';
      try { e.dataTransfer.setData('text/plain', JSON.stringify({ source: 'right', tracks: moved })); } catch (err) {}
      try { e.dataTransfer.setData('application/json', JSON.stringify({ source: 'right', tracks: moved })); } catch (err) {}
    });

    row.addEventListener('dragend', () => {
      row.classList.remove('dragging');
      window.__draggedTracksFromRight = null;
    });

    DOM.rightItemsContainer.appendChild(row);
  });
}

function handleRightRowClick(e, trackId, index) {
  if (e.metaKey || e.ctrlKey || e.shiftKey) {
    window.getSelection()?.removeAllRanges();
  }
  if (e.metaKey || e.ctrlKey) {
    if (state.rightSelectedIds.has(trackId)) {
      state.rightSelectedIds.delete(trackId);
    } else {
      state.rightSelectedIds.add(trackId);
    }
    state.rightLastSelectedId = trackId;
    state.rightLastClickedIndex = index;
    updateRightSelectionUI();
  } else if (e.shiftKey) {
    const start = Math.min(state.rightLastClickedIndex, index);
    const end = Math.max(state.rightLastClickedIndex, index);
    for (let i = start; i <= end; i++) {
      state.rightSelectedIds.add(state.rightTracks[i].id);
    }
    state.rightLastSelectedId = trackId;
    updateRightSelectionUI();
  } else {
    if (state.rightSelectedIds.has(trackId) && state.rightSelectedIds.size === 1) {
      state.rightSelectedIds.clear();
    } else {
      state.rightSelectedIds.clear();
      state.rightSelectedIds.add(trackId);
    }
    state.rightLastClickedIndex = index;
    state.rightLastSelectedId = trackId;
    updateRightSelectionUI();
  }
}

function toggleRightSelection(trackId, isShift, index) {
  if (state.rightSelectedIds.has(trackId)) {
    state.rightSelectedIds.delete(trackId);
  } else {
    state.rightSelectedIds.add(trackId);
  }
  state.rightLastSelectedId = trackId;
  state.rightLastClickedIndex = index;
  updateRightSelectionUI();
}

function updateRightSelectionUI() {
  const rows1 = DOM.rightItemsContainer ? Array.from(DOM.rightItemsContainer.querySelectorAll('.right-item-row')) : [];
  const rows2 = DOM.discoveryItemsContainer ? Array.from(DOM.discoveryItemsContainer.querySelectorAll('.right-item-row')) : [];
  [...rows1, ...rows2].forEach(row => {
    const isSel = state.rightSelectedIds.has(row.dataset.trackId);
    row.classList.toggle('selected', isSel);
    const chk = row.querySelector('.right-item-checkbox');
    if (chk) chk.checked = isSel;
  });
  const count = state.rightSelectedIds.size;
  if (DOM.rightSelectedText) {
    DOM.rightSelectedText.textContent = `${count} selected`;
  }
  if (DOM.discoveryResultCountBadge) {
    DOM.discoveryResultCountBadge.textContent = t('discoveredCountBadge', { count: state.discovery.discoveredTracks.length });
  }
}

// ==========================================
// --- "🎲 Surprise Me!" Discovery Studio Engine ---
// ==========================================

function initDiscoveryPanel() {
  if (!DOM.discoveryGenerateBtn) return;

  // 1. Modifier Toggles (+ AND / - NOT)
  const setupModToggle = (btnEl, fieldKey) => {
    if (!btnEl) return;
    btnEl.addEventListener('click', (e) => {
      e.preventDefault();
      const current = state.discovery[fieldKey];
      const next = current === 'AND' ? 'NOT' : 'AND';
      state.discovery[fieldKey] = next;
      btnEl.classList.toggle('mod-and', next === 'AND');
      btnEl.classList.toggle('mod-not', next === 'NOT');
      btnEl.textContent = next === 'AND' ? '+ AND' : '- NOT';
    });
  };

  setupModToggle(DOM.discArtistModBtn, 'artistMod');
  setupModToggle(DOM.discGenreModBtn, 'genreMod');
  setupModToggle(DOM.discDecadeModBtn, 'decadeMod');
  setupModToggle(DOM.discTrackModBtn, 'trackMod');

  // 2. Typeahead Inputs Setup
  setupDiscoveryTypeahead(DOM.discoveryArtistInput, DOM.discoveryArtistSuggestions, 'artist', (item) => {
    addDiscoveryChip('artists', item.name, item.id, state.discovery.artistMod);
    DOM.discoveryArtistInput.value = '';
    DOM.discoveryArtistSuggestions.classList.add('hidden');
  });

  setupDiscoveryTypeahead(DOM.discoveryGenreInput, DOM.discoveryGenreSuggestions, 'genre', (item) => {
    addDiscoveryChip('genres', item.id || item.name, null, state.discovery.genreMod);
    DOM.discoveryGenreInput.value = '';
    DOM.discoveryGenreSuggestions.classList.add('hidden');
    syncGenrePillsUI();
  });

  setupDiscoveryTypeahead(DOM.discoveryTrackInput, DOM.discoveryTrackSuggestions, 'track', (item) => {
    const trackName = item.name || item.title || 'Unknown Track';
    const artistName = item.artist || '';
    const label = artistName ? `${trackName} - ${artistName}` : trackName;
    addDiscoveryChip('tracks', label, item.id, state.discovery.trackMod);
    DOM.discoveryTrackInput.value = '';
    DOM.discoveryTrackSuggestions.classList.add('hidden');
  });

  // Add Button Click / Enter fallback for custom text
  DOM.discoveryAddArtistBtn?.addEventListener('click', () => {
    const val = DOM.discoveryArtistInput.value.trim();
    if (val) {
      addDiscoveryChip('artists', val, null, state.discovery.artistMod);
      DOM.discoveryArtistInput.value = '';
      DOM.discoveryArtistSuggestions.classList.add('hidden');
    }
  });

  DOM.discoveryAddGenreBtn?.addEventListener('click', () => {
    const val = DOM.discoveryGenreInput.value.trim().toLowerCase();
    if (val) {
      addDiscoveryChip('genres', val, null, state.discovery.genreMod);
      DOM.discoveryGenreInput.value = '';
      DOM.discoveryGenreSuggestions.classList.add('hidden');
      syncGenrePillsUI();
    }
  });

  // 3. Categorized Quick Genre Cloud
  DOM.genreCatPills?.forEach(pill => {
    pill.addEventListener('click', () => {
      DOM.genreCatPills.forEach(p => p.classList.remove('active'));
      pill.classList.add('active');
      const cat = pill.dataset.category || pill.dataset.cat || pill.getAttribute('data-cat') || pill.textContent.trim() || 'Popular';
      state.discovery.selectedGenreCategory = cat;
      loadGenreCategoryPills(cat);
    });
  });
  loadGenreCategoryPills('Popular');

  // 4. 3-State Decade Pills
  const decadePills = DOM.discoveryDecadePills?.querySelectorAll('.tri-state-pill') || [];
  decadePills.forEach(pill => {
    pill.addEventListener('click', () => {
      const decadeVal = pill.dataset.decade;
      toggleTriStatePill(pill, 'decades', decadeVal);
    });
  });

  // 5. Active Queue Vibe Button
  DOM.discoveryActiveVibeBtn?.addEventListener('click', () => {
    state.discovery.useActiveVibe = !state.discovery.useActiveVibe;
    DOM.discoveryActiveVibeBtn.classList.toggle('active', state.discovery.useActiveVibe);
    DOM.discoveryActiveVibeBtn.textContent = state.discovery.useActiveVibe ? '🔮 Vibe Active' : t('blendQueueVibeBtn');
    if (state.discovery.useActiveVibe) {
      showToast('🔮 Active queue vibe blending enabled!');
    }
  });

  // 6. Strict Negative Exclusions & Switches
  DOM.discNotLiked?.addEventListener('change', (e) => {
    state.discovery.notLikedSongs = e.target.checked;
  });
  DOM.discNotPlaylists?.addEventListener('change', (e) => {
    state.discovery.notInPlaylists = e.target.checked;
  });
  DOM.discIgnoreBlacklist?.addEventListener('change', (e) => {
    state.discovery.ignoreBlacklist = e.target.checked;
  });
  DOM.discRecentDaysRadios?.forEach(radio => {
    radio.addEventListener('change', (e) => {
      if (e.target.checked) {
        state.discovery.notRecentlyPlayedDays = parseInt(e.target.value, 10);
      }
    });
  });
  DOM.discLiveModeRadios?.forEach(radio => {
    radio.addEventListener('change', (e) => {
      if (e.target.checked) {
        if (e.target.value === 'not_live') {
          state.discovery.notLive = true;
          state.discovery.onlyLive = false;
        } else if (e.target.value === 'only_live') {
          state.discovery.notLive = false;
          state.discovery.onlyLive = true;
        } else {
          state.discovery.notLive = false;
          state.discovery.onlyLive = false;
        }
      }
    });
  });

  DOM.discRemixModeRadios?.forEach(radio => {
    radio.addEventListener('change', (e) => {
      if (e.target.checked) {
        if (e.target.value === 'not_remix') {
          state.discovery.notRemix = true;
          state.discovery.onlyRemix = false;
        } else if (e.target.value === 'only_remix') {
          state.discovery.notRemix = false;
          state.discovery.onlyRemix = true;
        } else {
          state.discovery.notRemix = false;
          state.discovery.onlyRemix = false;
        }
      }
    });
  });
  DOM.discLowPopularity?.addEventListener('change', (e) => {
    state.discovery.lowPopularityOnly = e.target.checked;
    DOM.hiddenGemTargetWrap?.classList.toggle('hidden', !e.target.checked);
  });
  DOM.discHiddenGemTargetRadios?.forEach(radio => {
    radio.addEventListener('change', (e) => {
      if (e.target.checked) {
        state.discovery.hiddenGemTarget = e.target.value;
      }
    });
  });

  // 7. Target Queue Size & Shuffle
  DOM.discTargetCountRadios?.forEach(radio => {
    radio.addEventListener('change', (e) => {
      if (e.target.checked) {
        state.discovery.targetCount = parseInt(e.target.value, 10);
      }
    });
  });
  DOM.discTrueShuffle?.addEventListener('change', (e) => {
    state.discovery.trueShuffle = e.target.checked;
  });

  // 8. Help Button
  DOM.discoveryHelpBtn?.addEventListener('click', () => {
    showConfirmModal({
      title: t('discoveryHelpTitle'),
      message: `${t('discoveryHelp')}\n\n• [+ AND]: Songs MUST match this seed\n• [- NOT]: Songs matching this will be STRICTLY EXCLUDED\n• 3-State Pills: Click to cycle Off ➔ [+ AND] ➔ [- NOT] ➔ Off\n• Strict filters guarantee 100% genuine new music discovery.`,
      confirmText: 'Got It!',
      onConfirm: () => DOM.confirmModal.classList.add('hidden')
    });
  });

  // 9. Generate Mix Button
  DOM.discoveryGenerateBtn?.addEventListener('click', generateDiscoveryMix);

  // 10. Transfer & Play Action Buttons
  DOM.discPlayDirectBtn?.addEventListener('click', () => {
    if (!state.discovery.discoveredTracks || state.discovery.discoveredTracks.length === 0) {
      showToast('No discovery tracks to play. Click "Generate Discovery Mix" first!', 'error');
      return;
    }
    const uris = state.discovery.discoveredTracks.map(t => t.uri);
    playTrackUris(uris, state.discovery.discoveredTracks[0].title);
  });
  DOM.discReplaceQueueBtn?.addEventListener('click', () => replaceMainQueue(false));
  DOM.discReplacePlayBtn?.addEventListener('click', () => replaceMainQueue(true));
  DOM.discAppendQueueBtn?.addEventListener('click', () => appendDiscoveryToMainQueue(false));
  DOM.discAppendSelectedBtn?.addEventListener('click', () => appendDiscoveryToMainQueue(true));
  DOM.discSelectAllBtn?.addEventListener('click', toggleSelectAllDiscovery);

  // 11. Drag & Drop Seeds (Artists, Decades, Songs)
  initDiscoveryDropZones();
}

// --- Discovery Drag & Drop Ingestion Helpers ---
function getDraggedTracksFromEvent(e) {
  let tracks = null;
  try {
    const raw = e.dataTransfer.getData('application/json') || e.dataTransfer.getData('text/plain');
    if (raw) {
      const parsed = JSON.parse(raw);
      if (parsed && Array.isArray(parsed.tracks) && parsed.tracks.length > 0) {
        tracks = parsed.tracks;
      } else if (Array.isArray(parsed) && parsed.length > 0) {
        tracks = parsed;
      }
    }
  } catch (err) {}

  if (!tracks && window.__draggedTracksFromRight && window.__draggedTracksFromRight.length > 0) {
    tracks = [...window.__draggedTracksFromRight];
  }
  if (!tracks && Array.isArray(draggedTrackData) && draggedTrackData.length > 0) {
    tracks = [...draggedTrackData];
  }
  if (!tracks && draggedTrackData && Array.isArray(draggedTrackData.tracks) && draggedTrackData.tracks.length > 0) {
    tracks = [...draggedTrackData.tracks];
  }

  setTimeout(() => {
    window.__draggedTracksFromRight = null;
    draggedTrackData = null;
  }, 50);

  return tracks || [];
}

function setupFieldDropZone(zoneEl, onDropTracks) {
  if (!zoneEl) return;

  let dragCounter = 0;

  zoneEl.addEventListener('dragenter', (e) => {
    e.preventDefault();
    dragCounter++;
    zoneEl.classList.add('drop-target-active');
  });

  zoneEl.addEventListener('dragover', (e) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'copy';
    if (!zoneEl.classList.contains('drop-target-active')) {
      zoneEl.classList.add('drop-target-active');
    }
  });

  zoneEl.addEventListener('dragleave', () => {
    dragCounter--;
    if (dragCounter <= 0) {
      dragCounter = 0;
      zoneEl.classList.remove('drop-target-active');
    }
  });

  zoneEl.addEventListener('drop', async (e) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounter = 0;
    zoneEl.classList.remove('drop-target-active');

    const tracks = getDraggedTracksFromEvent(e);
    if (!tracks || tracks.length === 0) return;

    await onDropTracks(tracks, e);
  });
}

function handleDropTracksOntoArtist(tracks) {
  const mod = state.discovery.artistMod || 'AND';
  let addedCount = 0;
  tracks.forEach(track => {
    const artist = (track.primary_artist || track.artist || '').trim();
    if (artist) {
      addDiscoveryChip('artists', artist, null, mod);
      addedCount++;
    }
  });
  if (addedCount > 0) {
    const isEs = state.currentLang === 'es';
    showToast(isEs 
      ? `🎤 ${addedCount} artista${addedCount === 1 ? '' : 's'} agregado${addedCount === 1 ? '' : 's'} como [${mod === 'AND' ? '+ Y' : '- NO'}]`
      : `🎤 Added ${addedCount} artist${addedCount === 1 ? '' : 's'} as [${mod === 'AND' ? '+ AND' : '- NOT'}]`);
  }
}

function handleDropTracksOntoSong(tracks) {
  const mod = state.discovery.trackMod || 'AND';
  let addedCount = 0;
  tracks.forEach(track => {
    const title = (track.title || '').trim();
    if (title) {
      addDiscoveryChip('tracks', title, track.id || null, mod);
      addedCount++;
    }
  });
  if (addedCount > 0) {
    const isEs = state.currentLang === 'es';
    showToast(isEs 
      ? `🎵 ${addedCount} canción${addedCount === 1 ? '' : 'es'} agregada${addedCount === 1 ? '' : 's'} como [${mod === 'AND' ? '+ Y' : '- NO'}]`
      : `🎵 Added ${addedCount} song${addedCount === 1 ? '' : 's'} as [${mod === 'AND' ? '+ AND' : '- NOT'}]`);
  }
}

function yearToDecade(year) {
  const y = parseInt(year, 10);
  if (isNaN(y)) return null;
  if (y >= 1960 && y <= 1969) return '60s';
  if (y >= 1970 && y <= 1979) return '70s';
  if (y >= 1980 && y <= 1989) return '80s';
  if (y >= 1990 && y <= 1999) return '90s';
  if (y >= 2000 && y <= 2009) return '00s';
  if (y >= 2010 && y <= 2019) return '10s';
  if (y >= 2020 && y <= 2029) return '20s';
  return null;
}

async function resolveTrackDecade(track) {
  if (!track) return null;
  // 1. Direct year on track
  if (track.year) {
    const d = yearToDecade(track.year);
    if (d) return d;
  }
  // 2. Year embedded in title or album
  const text = `${track.album || ''} ${track.title || ''}`;
  const match = text.match(/\b(19\d\d|20\d\d)\b/);
  if (match) {
    const d = yearToDecade(match[1]);
    if (d) return d;
  }
  // 3. Fallback: query backend resolution endpoint
  try {
    const artist = encodeURIComponent(track.artist || track.primary_artist || '');
    const title = encodeURIComponent(track.title || '');
    const res = await api(`/api/tracks/resolve-decade?artist=${artist}&title=${title}`);
    if (res && res.decade) {
      return res.decade;
    }
  } catch (err) {
    console.warn('Failed to resolve decade for track:', track, err);
  }
  return null;
}

function setDecadeModifier(decadeVal, modifier) {
  if (!decadeVal) return;
  const list = state.discovery.decades;
  const existingIdx = list.findIndex(item => item.value.toLowerCase() === decadeVal.toLowerCase());
  if (existingIdx >= 0) {
    list[existingIdx].modifier = modifier;
  } else {
    list.push({ value: decadeVal, id: null, modifier: modifier });
  }
  syncDecadePillsUI();
}

async function handleDropTracksOntoDecade(tracks, explicitDecade = null) {
  const mod = state.discovery.decadeMod || 'AND';
  if (explicitDecade) {
    setDecadeModifier(explicitDecade, mod);
    const isEs = state.currentLang === 'es';
    showToast(isEs 
      ? `📅 Década ${explicitDecade.toUpperCase()} establecida como [${mod === 'AND' ? '+ Y' : '- NO'}]`
      : `📅 Set decade ${explicitDecade.toUpperCase()} as [${mod === 'AND' ? '+ AND' : '- NOT'}]`);
    return;
  }

  const resolved = await Promise.all(tracks.map(t => resolveTrackDecade(t)));
  const resolvedDecades = new Set(resolved.filter(Boolean));

  if (resolvedDecades.size > 0) {
    resolvedDecades.forEach(dec => {
      setDecadeModifier(dec, mod);
    });
    const decList = Array.from(resolvedDecades).map(d => d.toUpperCase()).join(', ');
    const isEs = state.currentLang === 'es';
    showToast(isEs 
      ? `📅 Décadas detectadas: ${decList} agregadas como [${mod === 'AND' ? '+ Y' : '- NO'}]`
      : `📅 Detected decades: ${decList} added as [${mod === 'AND' ? '+ AND' : '- NOT'}]`);
  } else {
    const isEs = state.currentLang === 'es';
    showToast(isEs ? '⚠️ No se pudo determinar la década de las canciones' : '⚠️ Could not determine decade for dropped tracks', 'error');
  }
}

function initDiscoveryDropZones() {
  // 1. Auto-switch to Surprise Me tab on dragover
  DOM.tabRightDiscovery?.addEventListener('dragover', (e) => {
    e.preventDefault();
    if (state.rightTab !== 'discovery') {
      DOM.tabRightDiscovery.click();
    }
  });

  // 2. Drop zone: Artists (Section 1)
  setupFieldDropZone(DOM.discDropArtist, async (tracks) => {
    handleDropTracksOntoArtist(tracks);
  });

  // 3. Drop zone: Song Seed (Section 4)
  setupFieldDropZone(DOM.discDropTrack, async (tracks) => {
    handleDropTracksOntoSong(tracks);
  });

  // 4. Drop zone: Decades (Section 3)
  setupFieldDropZone(DOM.discDropDecade, async (tracks, event) => {
    const pill = event.target.closest('.tri-state-pill');
    const explicitDecade = pill ? pill.dataset.decade : null;
    await handleDropTracksOntoDecade(tracks, explicitDecade);
  });

  // 5. Individual decade pill dragover & visual highlight
  const decadePills = DOM.discoveryDecadePills?.querySelectorAll('.tri-state-pill') || [];
  decadePills.forEach(pill => {
    pill.addEventListener('dragover', (e) => {
      e.preventDefault();
      e.stopPropagation();
      e.dataTransfer.dropEffect = 'copy';
      pill.classList.add('drop-target-active');
    });
    pill.addEventListener('dragleave', () => {
      pill.classList.remove('drop-target-active');
    });
    pill.addEventListener('drop', () => {
      pill.classList.remove('drop-target-active');
    });
  });

  // Prevent default paste of drag payload into text inputs
  [DOM.discoveryArtistInput, DOM.discoveryTrackInput, DOM.discoveryGenreInput].forEach(inp => {
    if (!inp) return;
    inp.addEventListener('dragover', (e) => e.preventDefault());
  });
}

// --- Typeahead Auto-Suggest Helper ---
let typeaheadDebounce = null;
function setupDiscoveryTypeahead(inputEl, dropdownEl, type, onSelect) {
  if (!inputEl || !dropdownEl) return;

  const fetchAndShow = () => {
    const q = inputEl.value.trim();
    if (typeaheadDebounce) clearTimeout(typeaheadDebounce);
    if (!q || q.length < 1) {
      dropdownEl.classList.add('hidden');
      dropdownEl.innerHTML = '';
      return;
    }

    typeaheadDebounce = setTimeout(async () => {
      try {
        const resolvedType = typeof type === 'function' ? type() : type;
        const data = await api(`/api/discovery/suggest?type=${resolvedType}&q=${encodeURIComponent(q)}`);
        const suggestions = data.suggestions || data.results || [];
        renderTypeaheadDropdown(dropdownEl, suggestions, resolvedType, onSelect);
      } catch (err) {
        dropdownEl.classList.add('hidden');
      }
    }, 200);
  };

  inputEl.addEventListener('input', fetchAndShow);
  inputEl.addEventListener('focus', fetchAndShow);

  inputEl.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      const q = inputEl.value.trim();
      if (q) {
        onSelect({ name: q, title: q, artist: '', id: null });
      }
    } else if (e.key === 'Escape') {
      dropdownEl.classList.add('hidden');
    }
  });

  // Close dropdown on outside click
  document.addEventListener('click', (e) => {
    if (!inputEl.contains(e.target) && !dropdownEl.contains(e.target)) {
      dropdownEl.classList.add('hidden');
    }
  });
}

function renderTypeaheadDropdown(dropdownEl, items, type, onSelect) {
  dropdownEl.innerHTML = '';
  if (!items || items.length === 0) {
    dropdownEl.classList.add('hidden');
    return;
  }

  items.forEach(item => {
    const div = document.createElement('div');
    div.className = 'typeahead-item';

    const imgUrl = item.image_url || item.album_art_url;
    let imgHtml = '';
    if (imgUrl) {
      const isRound = type === 'artist' ? 'round' : '';
      imgHtml = `<img class="typeahead-thumb ${isRound}" src="${imgUrl}" alt="" loading="lazy">`;
    } else {
      const icon = type === 'artist' ? '🎤' : (type === 'track' ? '🎵' : '🎸');
      imgHtml = `<div class="typeahead-thumb placeholder-thumb" style="display:flex;align-items:center;justify-content:center;background:#333;font-size:11px;">${icon}</div>`;
    }

    const itemName = item.name || item.title || item.id || 'Unknown';
    let subText = item.subtitle || '';
    if (!subText && item.artist) {
      subText = item.artist + (item.album ? ` • ${item.album}` : '');
    }

    div.innerHTML = `
      ${imgHtml}
      <div class="typeahead-meta">
        <span class="typeahead-name">${escapeHtml(itemName)}</span>
        <span class="typeahead-sub">${escapeHtml(subText)}</span>
      </div>
    `;

    div.addEventListener('click', () => {
      onSelect(item);
    });

    dropdownEl.appendChild(div);
  });

  dropdownEl.classList.remove('hidden');
}

// --- Chips Management (Artists, Genres, Tracks) ---
function addDiscoveryChip(field, value, id, modifier) {
  if (!value) return;
  const list = state.discovery[field];
  // Allow having both + AND and - NOT for the same term (e.g. artist similarity seed + self exclusion)
  const existingIdx = list.findIndex(c => c.value.toLowerCase() === value.toLowerCase() && c.modifier === modifier);
  if (existingIdx < 0) {
    list.push({ value, id: id || null, modifier: modifier || 'AND' });
  }
  renderDiscoveryChips();
}

function removeDiscoveryChip(field, index) {
  state.discovery[field].splice(index, 1);
  renderDiscoveryChips();
  if (field === 'genres') syncGenrePillsUI();
  if (field === 'decades') syncDecadePillsUI();
}

function renderDiscoveryChips() {
  const renderChipsFor = (containerEl, field) => {
    if (!containerEl) return;
    containerEl.innerHTML = '';
    const chips = state.discovery[field] || [];

    chips.forEach((chip, idx) => {
      const chipEl = document.createElement('span');
      const isAnd = chip.modifier === 'AND';
      chipEl.className = `discovery-chip ${isAnd ? 'chip-and' : 'chip-not'}`;
      chipEl.innerHTML = `
        <span class="chip-mod-badge">${isAnd ? '+ AND' : '- NOT'}</span>
        <span>${escapeHtml(chip.value)}</span>
        <button class="chip-remove-btn" title="Remove">&times;</button>
      `;

      chipEl.querySelector('.chip-remove-btn').addEventListener('click', (e) => {
        e.stopPropagation();
        removeDiscoveryChip(field, idx);
      });

      // Clicking chip toggles its AND / NOT state
      chipEl.addEventListener('click', () => {
        chip.modifier = chip.modifier === 'AND' ? 'NOT' : 'AND';
        renderDiscoveryChips();
        if (field === 'genres') syncGenrePillsUI();
        if (field === 'decades') syncDecadePillsUI();
      });

      containerEl.appendChild(chipEl);
    });
  };

  renderChipsFor(DOM.discoveryArtistChips, 'artists');
  renderChipsFor(DOM.discoveryGenreChips, 'genres');
  renderChipsFor(DOM.discoveryTrackChips, 'tracks');
}

// --- Quick Genre Cloud & 3-State Pills Logic ---
async function loadGenreCategoryPills(category) {
  if (!DOM.discoveryGenrePills) return;
  DOM.discoveryGenrePills.innerHTML = '<span style="font-size:10px;color:#888;">Loading genres...</span>';

  try {
    const data = await api(`/api/discovery/genres?category=${encodeURIComponent(category)}`);
    const genres = data.genres || [];
    DOM.discoveryGenrePills.innerHTML = '';

    genres.forEach(genre => {
      const gId = typeof genre === 'object' ? (genre.id || genre.name) : genre;
      const gName = typeof genre === 'object' ? (genre.name || genre.id) : genre;
      const pill = document.createElement('span');
      pill.className = 'tri-state-pill';
      pill.dataset.genre = gId;
      pill.textContent = gName;

      pill.addEventListener('click', () => {
        toggleTriStatePill(pill, 'genres', gId);
      });

      DOM.discoveryGenrePills.appendChild(pill);
    });

    syncGenrePillsUI();
  } catch (err) {
    DOM.discoveryGenrePills.innerHTML = '<span style="font-size:10px;color:#888;">Failed to load genres.</span>';
  }
}

function toggleTriStatePill(pillEl, field, value) {
  const list = state.discovery[field];
  const existingIdx = list.findIndex(item => item.value.toLowerCase() === value.toLowerCase());

  if (existingIdx === -1) {
    // State 0 (Off) ➔ State 1 (+ AND)
    list.push({ value, id: null, modifier: 'AND' });
    pillEl.classList.remove('state-not');
    pillEl.classList.add('state-and');
    pillEl.textContent = `+ ${value}`;
  } else if (list[existingIdx].modifier === 'AND') {
    // State 1 (+ AND) ➔ State 2 (- NOT)
    list[existingIdx].modifier = 'NOT';
    pillEl.classList.remove('state-and');
    pillEl.classList.add('state-not');
    pillEl.textContent = `- ${value}`;
  } else {
    // State 2 (- NOT) ➔ State 0 (Off)
    list.splice(existingIdx, 1);
    pillEl.classList.remove('state-and', 'state-not');
    pillEl.textContent = value;
  }

  renderDiscoveryChips();
}

function syncGenrePillsUI() {
  if (!DOM.discoveryGenrePills) return;
  const pills = DOM.discoveryGenrePills.querySelectorAll('.tri-state-pill');
  const activeGenres = new Map(state.discovery.genres.map(g => [g.value.toLowerCase(), g.modifier]));

  pills.forEach(pill => {
    const val = (pill.dataset.genre || pill.textContent.replace(/^[+-]\s*/, '')).toLowerCase();
    pill.classList.remove('state-and', 'state-not');
    if (activeGenres.has(val)) {
      const mod = activeGenres.get(val);
      if (mod === 'AND') {
        pill.classList.add('state-and');
        pill.textContent = `+ ${pill.dataset.genre || val}`;
      } else {
        pill.classList.add('state-not');
        pill.textContent = `- ${pill.dataset.genre || val}`;
      }
    } else {
      pill.textContent = pill.dataset.genre || val;
    }
  });
}

function syncDecadePillsUI() {
  if (!DOM.discoveryDecadePills) return;
  const pills = DOM.discoveryDecadePills.querySelectorAll('.tri-state-pill');
  const activeDecades = new Map(state.discovery.decades.map(d => [d.value.toLowerCase(), d.modifier]));

  pills.forEach(pill => {
    const val = pill.dataset.decade.toLowerCase();
    pill.classList.remove('state-and', 'state-not');
    if (activeDecades.has(val)) {
      const mod = activeDecades.get(val);
      if (mod === 'AND') {
        pill.classList.add('state-and');
        pill.textContent = `+ ${pill.dataset.decade.toUpperCase()}`;
      } else {
        pill.classList.add('state-not');
        pill.textContent = `- ${pill.dataset.decade.toUpperCase()}`;
      }
    } else {
      pill.textContent = pill.dataset.decade.toUpperCase();
    }
  });
}

// --- Generate Discovery Mix ---
async function generateDiscoveryMix() {
  if (state.discovery.isGenerating) return;

  // Auto-commit any pending un-submitted text in seed inputs before generating
  if (DOM.discoveryArtistInput && DOM.discoveryArtistInput.value.trim()) {
    const val = DOM.discoveryArtistInput.value.trim();
    const mod = DOM.discArtistModBtn?.dataset.modifier || 'AND';
    if (!state.discovery.artists.some(a => a.value.toLowerCase() === val.toLowerCase())) {
      state.discovery.artists.push({ value: val, id: null, modifier: mod });
    }
    DOM.discoveryArtistInput.value = '';
    renderDiscoveryChips();
  }
  if (DOM.discoveryTrackInput && DOM.discoveryTrackInput.value.trim()) {
    const val = DOM.discoveryTrackInput.value.trim();
    const mod = DOM.discTrackModBtn?.dataset.modifier || 'AND';
    if (!state.discovery.tracks.some(t => t.value.toLowerCase() === val.toLowerCase())) {
      state.discovery.tracks.push({ value: val, id: null, modifier: mod });
    }
    DOM.discoveryTrackInput.value = '';
    renderDiscoveryChips();
  }
  if (DOM.discoveryKeywordInput && DOM.discoveryKeywordInput.value.trim()) {
    const val = DOM.discoveryKeywordInput.value.trim();
    const mod = DOM.discKeywordModBtn?.dataset.modifier || 'AND';
    if (!state.discovery.keywords.some(k => k.value.toLowerCase() === val.toLowerCase())) {
      state.discovery.keywords.push({ value: val, id: null, modifier: mod });
    }
    DOM.discoveryKeywordInput.value = '';
    renderDiscoveryChips();
  }

  state.discovery.isGenerating = true;
  const diceIcon = DOM.discoveryGenerateBtn?.querySelector('.dice-icon');
  const btnText = DOM.discoveryGenerateBtn?.querySelector('.btn-text');
  if (diceIcon) diceIcon.classList.add('spinning');
  if (btnText) btnText.textContent = t('generatingDiscoveryBtn');

  DOM.discoveryResultsSection?.classList.remove('hidden');
  if (DOM.discoveryItemsContainer) {
    DOM.discoveryItemsContainer.innerHTML = '<div class="search-placeholder-text">🎲 Harvesting music candidates, applying strict NOT filters & Shuffle...</div>';
  }

  const payload = {
    artists: state.discovery.artists,
    tracks: state.discovery.tracks,
    genres: state.discovery.genres,
    decades: state.discovery.decades,
    keywords: state.discovery.keywords,
    use_active_vibe: state.discovery.useActiveVibe,
    active_playlist_id: state.activeView === 'all' ? 'liked_songs' : state.activeView,
    not_liked_songs: state.discovery.notLikedSongs,
    not_in_playlists: state.discovery.notInPlaylists,
    not_recently_played_days: state.discovery.notRecentlyPlayedDays || null,
    not_live: state.discovery.notLive,
    not_remix: state.discovery.notRemix,
    only_live: state.discovery.onlyLive,
    only_remix: state.discovery.onlyRemix,
    low_popularity_only: state.discovery.lowPopularityOnly,
    hidden_gem_target: state.discovery.hiddenGemTarget || 'artist',
    target_count: state.discovery.targetCount,
    true_shuffle: state.discovery.trueShuffle,
    avoid_consecutive_artists: state.discovery.avoidConsecutiveArtists,
    ignore_blacklist: state.discovery.ignoreBlacklist || false
  };

  try {
    const res = await api('/api/discovery/generate', {
      method: 'POST',
      body: JSON.stringify(payload)
    });

    const tracks = res.tracks || [];
    state.discovery.discoveredTracks = tracks;
    state.rightTracks = tracks;

    DOM.discoveryResultsSection?.classList.remove('hidden');
    if (DOM.discoveryResultCountBadge) {
      DOM.discoveryResultCountBadge.textContent = t('discoveredCountBadge', { count: tracks.length });
    }
    
    renderDiscoveryResultsItems(tracks);
    showToast(t('discoveryToastSuccess', { count: tracks.length }));

    // Focus / smooth-scroll down to results inside Discovery studio
    setTimeout(() => {
      DOM.discoveryResultsSection?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 100);
  } catch (err) {
    if (DOM.discoveryItemsContainer) {
      DOM.discoveryItemsContainer.innerHTML = `<div class="search-placeholder-text" style="color:#ef4444;">Discovery generation failed: ${escapeHtml(err.message)}</div>`;
    }
    showToast(`Error: ${err.message}`, 'error');
  } finally {
    state.discovery.isGenerating = false;
    if (diceIcon) diceIcon.classList.remove('spinning');
    if (btnText) btnText.textContent = t('generateDiscoveryBtn');
  }
}

function renderDiscoveryResultsItems(tracks) {
  if (!DOM.discoveryItemsContainer) return;
  DOM.discoveryItemsContainer.innerHTML = '';
  state.rightSelectedIds.clear();
  updateRightSelectionUI();

  if (!tracks || tracks.length === 0) {
    DOM.discoveryItemsContainer.innerHTML = '<div class="search-placeholder-text">No tracks discovered with current filters. Try relaxing some restrictions.</div>';
    return;
  }

  tracks.forEach((track, index) => {
    const row = document.createElement('div');
    row.className = 'right-item-row';
    row.dataset.trackId = track.id;
    row.dataset.trackUri = track.uri || (track.id ? `yt:track:${track.id}` : '');
    row.dataset.trackTitle = track.title || '';
    row.dataset.index = index;
    row.draggable = true;

    const isThisPlaying = Boolean(
      state.currentPlayingTrackId && (track.id === state.currentPlayingTrackId || (track.uri && track.uri === state.currentPlayingTrackId))
    );
    if (isThisPlaying) {
      row.classList.add('now-playing-row');
    }

    // Selection Checkbox
    const chk = document.createElement('input');
    chk.type = 'checkbox';
    chk.className = 'right-item-checkbox';
    chk.checked = state.rightSelectedIds.has(track.id);
    chk.addEventListener('click', (e) => {
      e.stopPropagation();
      toggleRightSelection(track.id, e.shiftKey, index);
    });
    row.appendChild(chk);

    // Play Button on left
    const playBtnTitle = isThisPlaying
      ? (state.currentLang === 'es' ? 'Pausar/Reanudar reproducción' : 'Pause/Resume playback')
      : (state.currentLang === 'es' ? 'Reproducir canción' : 'Play track');

    const playBtn = document.createElement('button');
    playBtn.className = `right-item-play-btn disc-item-play-btn ${isThisPlaying ? 'playing' : ''}`;
    playBtn.title = playBtnTitle;
    playBtn.setAttribute('data-tooltip-title', isThisPlaying ? t('tipPauseTrackTitle') : t('tipPlayTrackTitle'));
    playBtn.setAttribute('data-tooltip', isThisPlaying ? t('tipPauseTrack') : t('tipPlayTrack'));
    playBtn.textContent = isThisPlaying ? '🔊' : '▶';
    playBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      handlePlayRightTrack(track, index, 'discovery');
    });
    row.appendChild(playBtn);

    // Small Album Art
    const imgHtml = track.album_art_url
      ? `<img class="track-album-art" style="width:24px;height:24px;border-radius:3px;object-fit:cover;" src="${track.album_art_url}" alt="" loading="lazy">`
      : `<div class="track-album-art-placeholder" style="width:24px;height:24px;font-size:10px;">🎵</div>`;

    // Hidden Gem Indicator
    const isGem = Boolean(track.is_hidden_gem || (track.popularity !== undefined && track.popularity <= 42 && state.discovery.lowPopularityOnly));
    let gemBadgeHtml = '';
    if (isGem) {
      const isEs = state.currentLang === 'es';
      const gemLabel = isEs ? '💎 Joya Oculta' : '💎 Hidden Gem';
      const popVal = track.popularity !== undefined ? `${track.popularity}%` : (track.artist_popularity !== undefined ? `${track.artist_popularity}%` : '');
      const popBadgeText = popVal ? ` (${popVal})` : '';
      const tooltip = isEs
        ? `Joya Oculta: Índice de popularidad bajo (${popVal || 'desconocido'})`
        : `Hidden Gem: Low popularity metric (${popVal || 'unknown'})`;
      gemBadgeHtml = `<span class="hidden-gem-badge" title="${escapeHtml(tooltip)}">${gemLabel}${popBadgeText}</span>`;
    }

    const metaWrap = document.createElement('div');
    metaWrap.className = 'right-item-meta';
    metaWrap.style.flex = '1';
    metaWrap.style.minWidth = '0';
    metaWrap.innerHTML = `
      <div style="display:flex; align-items:center; gap:6px;">
        ${imgHtml}
        <div style="flex:1; min-width:0;">
          <div class="right-item-title" style="white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${escapeHtml(track.title)}</div>
          <div class="right-item-subtitle-row" style="display:flex; align-items:center; gap:4px; flex-wrap:wrap;">
            <span class="right-item-artist" style="white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${escapeHtml(track.artist)}</span>
            ${gemBadgeHtml}
          </div>
        </div>
      </div>
    `;
    row.appendChild(metaWrap);

    const actionsWrap = document.createElement('div');
    actionsWrap.className = 'right-item-actions';

    const isLiked = state.likedTrackIds.has(track.id) || Boolean(track.is_liked);
    const likeBtnTitle = isLiked
      ? (state.currentLang === 'es' ? 'Quitar de Canciones que te gustan' : 'Remove from Liked Songs')
      : (state.currentLang === 'es' ? 'Guardar en Canciones que te gustan' : 'Save to Liked Songs');

    const likeBtn = document.createElement('button');
    likeBtn.className = `right-item-like-btn ${isLiked ? 'liked' : ''}`;
    likeBtn.dataset.trackId = track.id;
    likeBtn.title = likeBtnTitle;
    likeBtn.innerHTML = getHeartSvg(isLiked);
    likeBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      handleToggleLikeTrack(track);
    });
    actionsWrap.appendChild(likeBtn);

    const durSpan = document.createElement('span');
    durSpan.className = 'right-item-dur';
    durSpan.textContent = formatDuration(track.duration_ms);
    actionsWrap.appendChild(durSpan);

    const addBtn = document.createElement('button');
    addBtn.className = 'right-item-add-btn';
    addBtn.title = state.currentLang === 'es' ? 'Añadir a lista principal' : 'Add to main workspace';
    addBtn.textContent = '+ Add';
    addBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      insertTracksIntoMainPanel([track], state.tracks.length);
    });
    actionsWrap.appendChild(addBtn);

    const blockBtn = document.createElement('button');
    blockBtn.className = 'right-item-block-btn';
    const mainArtist = track.primary_artist || (track.artist ? track.artist.split(',')[0].split(' feat.')[0].split(' ft.')[0].trim() : 'Unknown');
    blockBtn.title = state.currentLang === 'es'
      ? `Bloquear artista principal (${mainArtist}) de Surprise Me`
      : `Block main artist (${mainArtist}) from Surprise Me`;
    blockBtn.textContent = '🚫';
    blockBtn.addEventListener('click', async (e) => {
      e.stopPropagation();
      if (!mainArtist || mainArtist === 'Unknown') return;
      await addArtistToBlacklist(mainArtist);

      // Immediately purge all tracks where this artist is the main artist from the active Discovery view
      state.discovery.discoveredTracks = (state.discovery.discoveredTracks || []).filter(t => {
        const ma = t.primary_artist || (t.artist ? t.artist.split(',')[0].split(' feat.')[0].split(' ft.')[0].trim() : '');
        return ma.toLowerCase() !== mainArtist.toLowerCase();
      });
      state.rightTracks = [...state.discovery.discoveredTracks];
      if (DOM.discoveryResultCountBadge) {
        DOM.discoveryResultCountBadge.textContent = t('discoveredCountBadge', { count: state.discovery.discoveredTracks.length });
      }
      renderDiscoveryResultsItems(state.discovery.discoveredTracks);
    });
    actionsWrap.appendChild(blockBtn);

    row.appendChild(actionsWrap);

    const albumArtEl = metaWrap.querySelector('.track-album-art, .track-album-art-placeholder');
    if (albumArtEl) {
      albumArtEl.title = state.currentLang === 'es' ? 'Reproducir canción' : 'Play track';
      albumArtEl.addEventListener('click', (e) => {
        e.stopPropagation();
        handlePlayRightTrack(track, index, 'discovery');
      });
    }

    // Row selection on click
    row.addEventListener('click', (e) => {
      if (e.target.closest('.right-item-block-btn') || e.target.closest('.right-item-add-btn') || e.target.closest('.right-item-play-btn') || e.target.closest('.disc-item-play-btn') || e.target.closest('.right-item-like-btn') || e.target.closest('.track-album-art') || e.target.closest('.track-album-art-placeholder') || e.target.type === 'checkbox') return;
      handleRightRowClick(e, track.id, index);
    });

    // Double click to play directly
    row.addEventListener('dblclick', () => {
      handlePlayRightTrack(track, index, 'discovery');
    });

    // Multi-Select and Drag support
    row.addEventListener('dragstart', (e) => {
      let moved = [];
      if (state.rightSelectedIds.has(track.id)) {
        moved = tracks.filter(t => state.rightSelectedIds.has(t.id));
      } else {
        moved = [track];
        state.rightSelectedIds.clear();
        state.rightSelectedIds.add(track.id);
        updateRightSelectionUI();
      }
      window.__draggedTracksFromRight = moved;
      row.classList.add('dragging');
      e.dataTransfer.effectAllowed = 'copy';
      try { e.dataTransfer.setData('text/plain', JSON.stringify({ source: 'right', tracks: moved })); } catch (err) {}
      try { e.dataTransfer.setData('application/json', JSON.stringify({ source: 'right', tracks: moved })); } catch (err) {}
    });

    row.addEventListener('dragend', () => {
      row.classList.remove('dragging');
      window.__draggedTracksFromRight = null;
    });

    DOM.discoveryItemsContainer.appendChild(row);
  });
}

// --- Discovery Transfer Handlers (Replace vs Append) ---
async function replaceMainQueue(autoPlay = false) {
  if (!state.discovery.discoveredTracks || state.discovery.discoveredTracks.length === 0) {
    showToast('No discovery tracks to load. Click "Generate Discovery Mix" first!', 'error');
    return;
  }

  // Save current order to undo stack
  state.undoStack.push([...state.tracks]);

  // Overwrite active workspace queue with discovered tracks
  state.tracks = [...state.discovery.discoveredTracks];
  state.userCustomOrderIds = state.tracks.map(t => t.id);
  state.selectedIds.clear();

  renderTracksTable();
  showToast(t('discoveryToastReplaced', { count: state.tracks.length }));

  if (autoPlay && state.tracks.length > 0) {
    playFromIndex(0);
  }
}

async function appendDiscoveryToMainQueue(selectedOnly = false) {
  let tracksToAppend = [];
  if (selectedOnly) {
    tracksToAppend = state.rightTracks.filter(t => state.rightSelectedIds.has(t.id));
    if (tracksToAppend.length === 0) {
      showToast('Select songs in the discovery list first', 'error');
      return;
    }
  } else {
    tracksToAppend = state.discovery.discoveredTracks;
  }

  if (!tracksToAppend || tracksToAppend.length === 0) {
    showToast('No discovery tracks to append.', 'error');
    return;
  }

  await insertTracksIntoMainPanel(tracksToAppend, state.tracks.length);
  showToast(t('discoveryToastAppended', { count: tracksToAppend.length }));
}

function toggleSelectAllDiscovery() {
  const allSelected = state.rightSelectedIds.size === state.rightTracks.length && state.rightTracks.length > 0;
  state.rightSelectedIds.clear();
  if (!allSelected) {
    state.rightTracks.forEach(t => state.rightSelectedIds.add(t.id));
  }
  updateRightSelectionUI();
}


// --- Live Player Poller ---
let pollerTimer = null;

function startPlayerStatePoller() {
  if (pollerTimer) clearInterval(pollerTimer);
  const interval = (state.playerState && state.playerState.is_playing) ? 2500 : 4000;
  if (!document.hidden) {
    pollerTimer = setInterval(pollPlayerState, interval);
    pollPlayerState();
  }
}

document.addEventListener('visibilitychange', () => {
  if (document.hidden) {
    if (pollerTimer) { clearInterval(pollerTimer); pollerTimer = null; }
  } else {
    pollPlayerState();
    startPlayerStatePoller();
  }
});

// --- YouTube Music Audio Engine (IFrame Player) ---
let ytAudioPlayer = null;
let isYtAudioReady = false;
let ytProgressInterval = null;
let isScrubbing = false;

window.onYouTubeIframeAPIReady = function() {
  ytAudioPlayer = new YT.Player('yt-audio-player', {
    height: '100',
    width: '100',
    playerVars: {
      'autoplay': 1,
      'controls': 0,
      'disablekb': 1,
      'playsinline': 1,
      'origin': window.location.origin
    },
    events: {
      'onReady': () => {
        isYtAudioReady = true;
        console.log('✅ YouTube Music Audio Engine ready.');
        if (DOM.volumeSlider) {
          ytAudioPlayer.setVolume(parseInt(DOM.volumeSlider.value, 10) || 80);
        }
      },
      'onStateChange': onYtAudioStateChange,
      'onError': (e) => {
        console.warn('YouTube Audio Player warning/error code:', e.data);
      }
    }
  });
};

function syncPlayerStateToBackend() {
  if (!state.playerState) return;
  api('/api/player/state', {
    method: 'POST',
    body: JSON.stringify(state.playerState)
  }).catch(() => {});
}

function onYtAudioStateChange(event) {
  if (event.data === 1) { // YT.PlayerState.PLAYING
    if (!state.playerState) state.playerState = {};
    state.playerState.is_playing = true;
    if (ytAudioPlayer && typeof ytAudioPlayer.getDuration === 'function') {
      const durSec = ytAudioPlayer.getDuration() || 0;
      if (durSec > 0 && state.playerState.item) {
        state.playerState.item.duration_ms = Math.round(durSec * 1000);
      }
    }
    updatePlayerPlayPauseButton(true);
    startYtProgressTimer();
    syncPlayerStateToBackend();
  } else if (event.data === 2) { // YT.PlayerState.PAUSED
    if (state.playerState) state.playerState.is_playing = false;
    updatePlayerPlayPauseButton(false);
    stopYtProgressTimer();
    
    // Maintain exact paused progress position so the bar never resets or jumps to 100%
    if (ytAudioPlayer && typeof ytAudioPlayer.getCurrentTime === 'function' && state.playerState?.item) {
      const curSec = ytAudioPlayer.getCurrentTime() || 0;
      const durSec = ytAudioPlayer.getDuration() || (state.playerState.item.duration_ms ? state.playerState.item.duration_ms / 1000 : 0);
      if (durSec > 0) {
        state.playerState.progress_ms = Math.round(curSec * 1000);
        state.playerState.item.duration_ms = Math.round(durSec * 1000);
        if (!isScrubbing) {
          const pct = Math.min(100, Math.max(0, (curSec / durSec) * 100));
          DOM.progressBarFill.style.width = `${pct}%`;
        }
      }
    }
    syncPlayerStateToBackend();
  } else if (event.data === 0) { // YT.PlayerState.ENDED
    stopYtProgressTimer();
    playNextInActiveList();
  }
}

function startYtProgressTimer() {
  stopYtProgressTimer();
  ytProgressInterval = setInterval(() => {
    if (isScrubbing) return;
    if (ytAudioPlayer && typeof ytAudioPlayer.getCurrentTime === 'function' && state.playerState?.item) {
      const curSec = ytAudioPlayer.getCurrentTime() || 0;
      let durSec = ytAudioPlayer.getDuration() || 0;
      if (!durSec && state.playerState.item.duration_ms) {
        durSec = state.playerState.item.duration_ms / 1000;
      }
      if (durSec > 0) {
        const curMs = Math.round(curSec * 1000);
        const durMs = Math.round(durSec * 1000);
        state.playerState.progress_ms = curMs;
        state.playerState.item.duration_ms = durMs;
        const pct = Math.min(100, Math.max(0, (curSec / durSec) * 100));
        DOM.progressBarFill.style.width = `${pct}%`;

        // If the active playing track in table has 0 duration, backfill it dynamically
        if (state.currentPlayingTrackId && state.tracks) {
          const t = state.tracks.find(x => x.id === state.currentPlayingTrackId);
          if (t && (!t.duration_ms || t.duration_ms === 0 || !t.durationMs || t.durationMs === 0)) {
            t.duration_ms = durMs;
            t.durationMs = durMs;
            const rowDur = DOM.tracksTbody?.querySelector(`.track-row[data-track-id="${t.id}"] .col-duration`);
            if (rowDur) rowDur.textContent = formatDuration(durMs);
          }
        }
      }
    }
  }, 250);
}

function stopYtProgressTimer() {
  if (ytProgressInterval) {
    clearInterval(ytProgressInterval);
    ytProgressInterval = null;
  }
}

function playYouTubeTrack(track) {
  if (!track || !track.id) return;
  
  state.currentPlayingTrackId = track.id;
  state.currentPlayingTrackTitle = track.title;
  
  if (ytAudioPlayer && typeof ytAudioPlayer.loadVideoById === 'function') {
    ytAudioPlayer.loadVideoById(track.id);
    ytAudioPlayer.playVideo();
  }
  
  const trackDur = track.duration_ms || track.durationMs || 0;
  const mockState = {
    is_playing: true,
    progress_ms: 0,
    item: {
      id: track.id,
      uri: track.uri || `yt:track:${track.id}`,
      title: track.title,
      artist: track.artist,
      album: track.album || '',
      album_art_url: track.album_art_url || track.thumbnailUrl || '',
      duration_ms: trackDur
    }
  };
  state.playerState = mockState;
  updatePlayerUI(mockState);
  startYtProgressTimer();
  syncPlayerStateToBackend();
}

async function pollPlayerState() {
  if (document.hidden) return;
  try {
    const data = await api('/api/player/state');
    
    // Check if we have an active local YouTube player
    const hasLocalYt = ytAudioPlayer && typeof ytAudioPlayer.getPlayerState === 'function';
    const ytState = hasLocalYt ? ytAudioPlayer.getPlayerState() : -1;
    const isYtActive = (ytState === 1 || ytState === 2 || ytState === 3);
    
    if (isYtActive && state.playerState?.item) {
      // Local YouTube player is actively controlling playback; keep is_playing and progress
      state.playerState.is_playing = (ytState === 1);
      updatePlayerUI(state.playerState);
    } else {
      state.playerState = data;
      updatePlayerUI(data);
    }
  } catch (e) {}
}

const PLAYER_SVG_ICONS = {
  play: `<svg class="player-svg-icon play-icon" viewBox="0 0 16 16"><path d="M3 1.713a.7.7 0 0 1 1.05-.607l10.89 6.288a.7.7 0 0 1 0 1.212L4.05 14.894A.7.7 0 0 1 3 14.288V1.713z"/></svg>`,
  pause: `<svg class="player-svg-icon pause-icon" viewBox="0 0 16 16"><path d="M2.7 1a.7.7 0 0 0-.7.7v12.6a.7.7 0 0 0 .7.7h2.6a.7.7 0 0 0 .7-.7V1.7a.7.7 0 0 0-.7-.7H2.7zm8 0a.7.7 0 0 0-.7.7v12.6a.7.7 0 0 0 .7.7h2.6a.7.7 0 0 0 .7-.7V1.7a.7.7 0 0 0-.7-.7h-2.6z"/></svg>`
};

function updatePlayerPlayPauseButton(isPlaying) {
  if (!DOM.ctrlPlaypause) return;
  DOM.ctrlPlaypause.innerHTML = isPlaying ? PLAYER_SVG_ICONS.pause : PLAYER_SVG_ICONS.play;
  DOM.ctrlPlaypause.classList.toggle('is-playing', !!isPlaying);
}

function updatePlayerUI(data) {
  const hasLocalYt = ytAudioPlayer && typeof ytAudioPlayer.getPlayerState === 'function';
  const ytState = hasLocalYt ? ytAudioPlayer.getPlayerState() : -1;
  const isYtActive = (ytState === 1 || ytState === 2 || ytState === 3);

  let isPlaying = data && data.is_playing;
  if (isYtActive) {
    isPlaying = (ytState === 1);
  }

  const item = data && data.item;

  if (item) {
    state.currentPlayingTrackId = item.id;
    state.currentPlayingTrackTitle = item.title;

    // Bottom Bar
    DOM.playerTitle.textContent = item.title;
    DOM.playerArtist.textContent = item.artist;
    updatePlayerPlayPauseButton(isPlaying);

    // Progress Bar:
    if (!isScrubbing) {
      if (isYtActive) {
        const curSec = ytAudioPlayer.getCurrentTime() || 0;
        let durSec = ytAudioPlayer.getDuration() || 0;
        if (!durSec && item.duration_ms) {
          durSec = item.duration_ms / 1000;
        }
        if (durSec > 0) {
          const pct = Math.min(100, Math.max(0, (curSec / durSec) * 100));
          DOM.progressBarFill.style.width = `${pct}%`;
        }
      } else {
        const prog = (data && data.progress_ms) || 0;
        const dur = (item && item.duration_ms) || 0;
        if (dur > 0) {
          const pct = Math.min(100, Math.max(0, (prog / dur) * 100));
          DOM.progressBarFill.style.width = `${pct}%`;
        } else {
          DOM.progressBarFill.style.width = '0%';
        }
      }
    }

    // Dynamic Vibrant Green Font Highlighting in Table (strictly by ID / URI)
    const rows = DOM.tracksTbody.querySelectorAll('.track-row');
    rows.forEach(row => {
      const rowId = row.dataset.trackId;
      const rowUri = row.dataset.trackUri;

      const isThisTrack = Boolean(
        (item.id && (rowId === item.id || rowUri === item.id)) ||
        (item.uri && rowUri === item.uri)
      );

      row.classList.toggle('now-playing-row', isThisTrack);
      const rowPlayBtn = row.querySelector('.row-play-btn');
      if (rowPlayBtn) {
        rowPlayBtn.classList.toggle('playing', isThisTrack && isPlaying);
        rowPlayBtn.textContent = isThisTrack ? (isPlaying ? '🔊' : '▶') : '▶';
      }
    });

    // Update right panel items strictly by ID / URI (so songs with same title are NOT all marked playing)
    const sideRows = document.querySelectorAll('.right-item-row');
    sideRows.forEach(sRow => {
      const sId = sRow.dataset.trackId;
      const sUri = sRow.dataset.trackUri;
      const isSideTrack = Boolean(
        (item.id && (sId === item.id || sUri === item.id)) ||
        (item.uri && sUri === item.uri)
      );
      sRow.classList.toggle('now-playing-row', isSideTrack);
      const playBtn = sRow.querySelector('.right-item-play-btn');
      if (playBtn) {
        if (isSideTrack) {
          playBtn.classList.toggle('playing', isPlaying);
          playBtn.textContent = isPlaying ? '🔊' : '▶';
          const pTitle = isPlaying
            ? (state.currentLang === 'es' ? 'Pausar reproducción' : 'Pause playback')
            : (state.currentLang === 'es' ? 'Reanudar reproducción' : 'Resume playback');
          playBtn.title = pTitle;
        } else {
          playBtn.classList.remove('playing');
          playBtn.textContent = '▶';
          playBtn.title = state.currentLang === 'es' ? 'Reproducir canción' : 'Play track';
        }
      }
    });

    // User can freely scroll up and down the main queue while playing.
    // Centering is controlled via the dedicated '🎯 Center on Playing Song' button.
  } else {
    state.currentPlayingTrackId = null;
    state.currentPlayingTrackTitle = null;
    DOM.playerTitle.textContent = 'Not Playing';
    DOM.playerArtist.textContent = 'Open YouTube Music on Mac or Android';
    updatePlayerPlayPauseButton(false);
    DOM.progressBarFill.style.width = '0%';

    document.querySelectorAll('.right-item-row.now-playing-row').forEach(r => r.classList.remove('now-playing-row'));
    document.querySelectorAll('.right-item-play-btn').forEach(btn => {
      btn.classList.remove('playing');
      btn.textContent = '▶';
      btn.title = state.currentLang === 'es' ? 'Reproducir canción' : 'Play track';
    });
  }
}

// --- Center on Playing Song ---
function centerOnPlayingSong() {
  const activeRow = DOM.tracksTbody?.querySelector('.track-row.now-playing-row');
  if (activeRow) {
    activeRow.scrollIntoView({ behavior: 'smooth', block: 'center' });
    activeRow.classList.add('flash-locate');
    setTimeout(() => activeRow.classList.remove('flash-locate'), 1200);
  } else if (state.currentPlayingTrackId) {
    const isEs = state.currentLang === 'es';
    showToast(isEs ? 'ℹ️ La canción en reproducción no está en la vista actual' : 'ℹ️ Playing song is not in the currently displayed view', 'info');
  } else {
    const isEs = state.currentLang === 'es';
    showToast(isEs ? 'ℹ️ No hay ninguna canción reproduciéndose' : 'ℹ️ No song is currently playing', 'info');
  }
}

// --- Save As Playlist Button State Updater ---
function updateSaveAsPlaylistButtonState() {
  if (!DOM.saveAsPlaylistBtn) return;
  const count = state.tracks ? state.tracks.length : 0;
  DOM.saveAsPlaylistBtn.disabled = (count === 0);
  if (count === 0) {
    DOM.saveAsPlaylistBtn.classList.remove('btn-primary');
    DOM.saveAsPlaylistBtn.classList.add('btn-secondary');
    DOM.saveAsPlaylistBtn.setAttribute('data-tooltip-title', t('tipSaveAsPlaylistTitle') || 'Save as Playlist');
    DOM.saveAsPlaylistBtn.setAttribute('data-tooltip', t('tipSaveAsPlaylistEmpty') || 'The active list has no songs to save.');
  } else {
    DOM.saveAsPlaylistBtn.classList.remove('btn-secondary');
    DOM.saveAsPlaylistBtn.classList.add('btn-primary');
    DOM.saveAsPlaylistBtn.setAttribute('data-tooltip-title', t('tipSaveAsPlaylistTitle') || 'Save as Playlist');
    DOM.saveAsPlaylistBtn.setAttribute('data-tooltip', t('tipSaveAsPlaylistActive', { count }) || `Save or overwrite a YouTube Music playlist with these ${count} songs.`);
  }
}

// --- Create YouTube Music Playlist Modal ---
function openCreatePlaylistModal(fromActiveList = false) {
  state.playlistCreationFromActiveList = fromActiveList;
  let count = 0;
  if (fromActiveList) {
    count = state.tracks.length;
    const isEs = state.currentLang === 'es';
    DOM.createPlaylistCountHint.textContent = isEs
      ? `Creará o sobrescribirá una playlist con las ${count} canciones de tu lista activa en su orden actual.`
      : `Will create or overwrite a playlist containing ${count} songs from your active list in their current order.`;
    DOM.newPlaylistName.value = `Kiki's Mix ${new Date().toLocaleDateString()}`;
  } else {
    count = state.selectedIds.size;
    const isEs = state.currentLang === 'es';
    DOM.createPlaylistCountHint.textContent = isEs
      ? `Creará o sobrescribirá una playlist con las ${count} canciones seleccionadas.`
      : `Will create or overwrite a playlist containing ${count} selected song${count === 1 ? '' : 's'}.`;
    DOM.newPlaylistName.value = `Kiki's Mix ${new Date().toLocaleDateString()}`;
  }
  DOM.createPlaylistModal.classList.remove('hidden');
  DOM.newPlaylistName.focus();
}

async function handleCreatePlaylist() {
  const name = DOM.newPlaylistName.value.trim();
  const desc = DOM.newPlaylistDesc.value.trim();
  let trackIds = [];
  if (state.playlistCreationFromActiveList) {
    trackIds = state.tracks.map(t => t.id);
  } else {
    trackIds = Array.from(state.selectedIds);
  }

  if (!name || trackIds.length === 0) return;

  const isEs = state.currentLang === 'es';
  // Check if a playlist with this exact name already exists in state.allPlaylists
  const existingPlaylist = state.allPlaylists.find(p => p.name.trim().toLowerCase() === name.toLowerCase());

  if (existingPlaylist) {
    // Hide creation modal and prompt for overwrite confirmation
    DOM.createPlaylistModal.classList.add('hidden');
    
    const confirmTitle = isEs 
      ? `⚠️ ¿Sobrescribir playlist "${existingPlaylist.name}"?` 
      : `⚠️ Overwrite Playlist "${existingPlaylist.name}"?`;
    const confirmMsg = isEs
      ? `Ya existe una playlist llamada "${existingPlaylist.name}" con ${existingPlaylist.total_tracks} canciones.\n\n¿Deseas sobrescribirla con estas ${trackIds.length} canciones? Esto actualizará la lista en YouTube Music y en tu biblioteca local.`
      : `A playlist named "${existingPlaylist.name}" already exists (${existingPlaylist.total_tracks} tracks).\n\nDo you want to overwrite it with these ${trackIds.length} tracks? This will replace the playlist's tracks on YouTube Music and in your local library.`;
    const confirmBtnText = isEs ? '⚠️ Sobrescribir Playlist' : '⚠️ Overwrite Playlist';

    showConfirmModal({
      title: confirmTitle,
      message: confirmMsg,
      confirmText: confirmBtnText,
      onConfirm: async () => {
        DOM.confirmModal.classList.add('hidden');
        await executePlaylistSave(name, desc, trackIds, true, existingPlaylist.id);
      }
    });
    return;
  }

  // If new playlist name, proceed directly
  await executePlaylistSave(name, desc, trackIds, false, null);
}

async function executePlaylistSave(name, desc, trackIds, overwrite = false, playlistId = null) {
  DOM.confirmCreatePlaylistModal.disabled = true;
  DOM.confirmCreatePlaylistModal.textContent = state.currentLang === 'es' ? 'Guardando playlist...' : 'Saving Playlist...';

  const selectedTracks = state.playlistCreationFromActiveList
    ? state.tracks
    : state.tracks.filter(t => state.selectedIds.has(t.id));

  try {
    const res = await api('/api/playlists/create', {
      method: 'POST',
      body: JSON.stringify({
        name: name,
        description: desc,
        track_ids: trackIds,
        tracks: selectedTracks,
        overwrite: overwrite,
        playlist_id: playlistId
      })
    });
    
    const isEs = state.currentLang === 'es';
    if (res.overwritten) {
      showToast(isEs ? `💾 ¡Playlist "${name}" sobrescrita localmente con ${trackIds.length} canciones!` : `💾 Overwrote local Playlist "${name}" with ${trackIds.length} songs!`);
    } else {
      showToast(isEs ? `💾 ¡Playlist "${name}" guardada localmente (${trackIds.length} canciones)!` : `💾 Saved Playlist "${name}" locally (${trackIds.length} songs)!`);
    }
    
    DOM.createPlaylistModal.classList.add('hidden');
    await loadPlaylists();
    updateSaveAsPlaylistButtonState();
  } catch (e) {
    showToast('Failed to save playlist: ' + e.message, 'error');
  } finally {
    DOM.confirmCreatePlaylistModal.disabled = false;
    DOM.confirmCreatePlaylistModal.textContent = state.currentLang === 'es' ? '💾 Guardar localmente' : '💾 Save Locally';
  }
}

// --- Smart Playlist Sync & Conflict Resolution ---
async function handleSyncPlaylist(p) {
  if (!p) return;
  try {
    showToast(`Checking sync status for "${p.name}"...`);
    const check = await api(`/api/playlists/${p.id}/sync-check`, { method: 'POST' });
    
    if (check.has_conflict) {
      activeSyncPlaylist = p;
      const isEs = state.currentLang === 'es';
      const remoteDate = check.remote_updated_at ? new Date(check.remote_updated_at).toLocaleString() : 'recently';
      const localDate = new Date(check.local_updated_at).toLocaleString();
      
      DOM.syncConflictDesc.innerHTML = isEs
        ? `⚠️ <strong>Conflicto detectado:</strong> La playlist en YouTube Music tiene cambios más recientes (${check.remote_count} canciones, modificado el ${remoteDate}) que tu copia local (${check.local_count} canciones, modificado el ${localDate}).<br><br>Por defecto se sugiere sincronizar de la app a YouTube, pero puedes elegir la dirección que prefieras:`
        : `⚠️ <strong>Conflict detected:</strong> The playlist on YouTube Music has newer changes (${check.remote_count} tracks, updated ${remoteDate}) than your local copy (${check.local_count} tracks, updated ${localDate}).<br><br>By default, sync pushes from app to YouTube, but you can choose your preferred direction:`;
        
      DOM.syncConflictModal.classList.remove('hidden');
    } else {
      // Direct sync: App to YouTube
      const res = await api(`/api/playlists/${p.id}/sync`, {
        method: 'POST',
        body: JSON.stringify({ direction: 'app_to_yt' })
      });
      showToast(res.message || `☁️ Sincronizada "${p.name}" exitosamente`);
      await loadPlaylists();
      if (state.activeView === p.id) {
        await loadTracks();
      }
    }
  } catch (e) {
    showToast('Sync error: ' + e.message, 'error');
  }
}

// --- Context Menu for Tracks ---
let contextMenuIndex = 0;
function openContextMenu(x, y, index) {
  contextMenuIndex = index;
  hideAllContextMenus();
  const track = state.tracks[index];
  const ctxToggleLike = document.getElementById('ctx-toggle-like');
  if (track && ctxToggleLike) {
    const isLiked = state.likedTrackIds.has(track.id) || Boolean(track.is_liked);
    ctxToggleLike.textContent = isLiked ? t('ctxRemoveFromLiked') : t('ctxSaveToLiked');
  }
  const menu = DOM.customContextMenu;
  menu.style.left = `${Math.min(x, window.innerWidth - 200)}px`;
  menu.style.top = `${Math.min(y, window.innerHeight - 240)}px`;
  menu.classList.remove('hidden');
}

function hideAllContextMenus() {
  DOM.customContextMenu?.classList.add('hidden');
  DOM.playlistContextMenu?.classList.add('hidden');
}

// --- Event Listeners Setup ---
function initEventListeners() {
  initBlacklistEvents();

  // Dual-Flag Language Switcher (🇬🇧 English <-> 🇦🇷 Español Argentina)
  DOM.langBtnEn?.addEventListener('click', (e) => {
    e.stopPropagation();
    if (state.currentLang !== 'en') {
      applyLanguage('en');
      showToast('🇬🇧 Language switched to English');
    }
  });

  DOM.langBtnEs?.addEventListener('click', (e) => {
    e.stopPropagation();
    if (state.currentLang !== 'es') {
      applyLanguage('es');
      showToast('🇦🇷 Idioma cambiado a Español (Argentina)');
    }
  });

  DOM.langToggleGroup?.addEventListener('click', () => {
    const nextLang = state.currentLang === 'en' ? 'es' : 'en';
    applyLanguage(nextLang);
    showToast(nextLang === 'es' ? '🇦🇷 Idioma cambiado a Español (Argentina)' : '🇬🇧 Language switched to English');
  });

  // Sync Library Buttons
  DOM.topSyncBtn?.addEventListener('click', () => triggerAutoSync(false));
  DOM.sidebarSyncBtn?.addEventListener('click', () => triggerAutoSync(false));

  // Search Filter in active list
  DOM.searchInput?.addEventListener('input', (e) => {
    state.searchQuery = e.target.value;
    DOM.clearSearchBtn?.classList.toggle('hidden', !state.searchQuery);
    loadTracks();
  });
  DOM.clearSearchBtn?.addEventListener('click', () => {
    DOM.searchInput.value = '';
    state.searchQuery = '';
    DOM.clearSearchBtn?.classList.add('hidden');
    loadTracks();
  });

  // Sidebar Nav
  DOM.navItems?.forEach(item => {
    attachNavItemDropHandler(item);
    item.addEventListener('click', () => {
      document.querySelectorAll('.nav-item').forEach(i => i.classList.remove('active'));
      item.classList.add('active');
      state.activeView = item.dataset.view;
      loadTracks();
    });
  });

  DOM.selectAllCheckbox?.addEventListener('change', (e) => {
    if (e.target.checked) {
      state.tracks.forEach(t => state.selectedIds.add(t.id));
    } else {
      state.selectedIds.clear();
    }
    updateSelectionUI();
  });

  document.querySelectorAll('#tracks-table th.sortable').forEach(th => {
    th.addEventListener('click', () => {
      const col = th.dataset.sort;
      if (col) applyColumnSort(col);
    });
  });

  // Main Toolbar Buttons
  DOM.playActiveListBtn?.addEventListener('click', () => playFromIndex(0));
  DOM.shuffleActiveListBtn?.addEventListener('click', toggleTrueShuffle);
  DOM.locatePlayingBtn?.addEventListener('click', centerOnPlayingSong);
  DOM.reloadViewBtn?.addEventListener('click', () => {
    loadTracks();
    showToast('🔄 Reloaded original list from library');
  });
  DOM.clearQueueBtn?.addEventListener('click', () => {
    if (!state.tracks || state.tracks.length === 0) return;
    state.undoStack.push([...state.tracks]);
    state.tracks = [];
    state.selectedIds.clear();
    state.userCustomOrderIds = [];
    renderTracksTable();
    updateSelectionUI();
    showToast('🗑️ Cleared workspace queue. (Press Cmd+Z to undo or click 🔄 Reload to restore)');
  });
  DOM.saveAsPlaylistBtn?.addEventListener('click', () => openCreatePlaylistModal(true));
  DOM.bakeShuffleBtn?.addEventListener('click', async () => {
    if (!state.tracks || state.tracks.length === 0) {
      showToast(state.currentLang === 'es' ? '⚠️ La cola activa está vacía' : '⚠️ Active queue is empty', 'warning');
      return;
    }
    const isEs = state.currentLang === 'es';
    const dateStr = new Date().toLocaleDateString();
    showConfirmModal({
      title: isEs ? '🔥 Bake Shuffle a YouTube Music' : '🔥 Bake Shuffle to YouTube Music',
      message: isEs
        ? `Se creará una nueva playlist permanente en YouTube Music con las ${state.tracks.length} canciones en su orden aleatorio actual.`
        : `This will create a new static playlist on YouTube Music containing these ${state.tracks.length} songs in their current randomized order.`,
      showInput: true,
      inputValue: `Bake Shuffle - ${dateStr}`,
      inputLabel: isEs ? 'Nombre de la Playlist:' : 'Playlist Name:',
      confirmText: isEs ? '🔥 Bake & Exportar' : '🔥 Bake & Export',
      onConfirm: async () => {
        const plName = DOM.confirmModalInput.value.trim() || `Bake Shuffle - ${dateStr}`;
        DOM.confirmModal.classList.add('hidden');
        showToast(isEs ? '🔥 Guardando y exportando a YouTube Music...' : '🔥 Baking and exporting to YouTube Music...', 'info');
        try {
          const res = await api('/api/queue/bake-shuffle', {
            method: 'POST',
            body: JSON.stringify({ name: plName })
          });
          showToast(res.message || (isEs ? '✅ ¡Playlist exportada con éxito!' : '✅ Playlist baked and exported successfully!'));
          await loadPlaylists();
        } catch (e) {
          showToast('Export error: ' + e.message, 'error');
        }
      }
    });
  });
  DOM.resetOrderBtn?.addEventListener('click', resetToUserOrder);
  DOM.saveOrderBtn?.addEventListener('click', saveAsUserOrder);
  DOM.lockOrderBtn?.addEventListener('click', () => {
    state.isOrderLocked = !state.isOrderLocked;
    DOM.lockOrderBtn.textContent = state.isOrderLocked ? '🔒 Locked' : '🔓 Unlocked';
    DOM.lockOrderBtn.classList.toggle('btn-accent', state.isOrderLocked);
    renderTracksTable();
    showToast(state.isOrderLocked ? '🔒 List order locked (drags and shuffles disabled)' : '🔓 List order unlocked');
  });

  // Batch Action Bar
  DOM.batchCreatePlaylistBtn?.addEventListener('click', () => openCreatePlaylistModal(false));

  DOM.batchShuffleBtn?.addEventListener('click', () => {
    if (state.isOrderLocked) {
      showToast('🔒 Active list order is locked. Unlock it to shuffle!', 'error');
      return;
    }
    const selTracks = state.tracks.filter(t => state.selectedIds.has(t.id));
    const uris = selTracks.map(t => t.uri);
    api('/api/player/play', {
      method: 'POST',
      body: JSON.stringify({ uris: uris, true_shuffle: true, device_id: DOM.deviceSelect?.value || null })
    });
    showToast(`🔀 Shuffled ${uris.length} selected tracks`);
  });

  DOM.clearSelectionBtn?.addEventListener('click', () => {
    state.selectedIds.clear();
    updateSelectionUI();
  });

  // Player Controls
  DOM.playerLocateBtn?.addEventListener('click', centerOnPlayingSong);
  DOM.playerTrackInfo?.addEventListener('click', centerOnPlayingSong);
  DOM.ctrlPlaypause?.addEventListener('click', async () => {
    if (ytAudioPlayer && typeof ytAudioPlayer.getPlayerState === 'function') {
      const pState = ytAudioPlayer.getPlayerState();
      if (pState === 1) { // playing -> pause
        ytAudioPlayer.pauseVideo();
        updatePlayerPlayPauseButton(false);
        if (state.playerState) state.playerState.is_playing = false;
        syncPlayerStateToBackend();
      } else { // paused -> play
        ytAudioPlayer.playVideo();
        updatePlayerPlayPauseButton(true);
        if (state.playerState) state.playerState.is_playing = true;
        syncPlayerStateToBackend();
      }
    } else {
      await api('/api/player/control', { method: 'POST', body: JSON.stringify({ action: 'playpause' }) });
      pollPlayerState();
    }
  });
  DOM.ctrlNext?.addEventListener('click', () => {
    playNextInActiveList();
  });
  DOM.ctrlPrev?.addEventListener('click', () => {
    playPrevInActiveList();
  });
  DOM.ctrlTrueShuffle?.addEventListener('click', toggleTrueShuffle);

  // --- Progress Bar Seek & Drag Scrubber ---
  function getSeekPercentage(e) {
    if (!DOM.progressBarWrap) return 0;
    const rect = DOM.progressBarWrap.getBoundingClientRect();
    if (rect.width <= 0) return 0;
    const clientX = (e.clientX !== undefined) ? e.clientX : (e.touches && e.touches[0] ? e.touches[0].clientX : (e.changedTouches && e.changedTouches[0] ? e.changedTouches[0].clientX : 0));
    const clickX = clientX - rect.left;
    return Math.max(0, Math.min(1, clickX / rect.width));
  }

  async function performSeek(pct) {
    let durSec = 0;
    if (ytAudioPlayer && typeof ytAudioPlayer.getDuration === 'function') {
      durSec = ytAudioPlayer.getDuration() || 0;
    }
    if (!durSec && state.playerState?.item?.duration_ms) {
      durSec = state.playerState.item.duration_ms / 1000;
    }
    if (!durSec && state.tracks && state.currentPlayingTrackId) {
      const t = state.tracks.find(x => x.id === state.currentPlayingTrackId);
      if (t && (t.duration_ms || t.durationMs)) {
        durSec = (t.duration_ms || t.durationMs) / 1000;
      }
    }
    if (durSec <= 0) durSec = 180; // fallback default 3 mins if duration unknown

    const newPosSec = pct * durSec;
    const newPosMs = Math.round(newPosSec * 1000);

    if (state.playerState) {
      state.playerState.progress_ms = newPosMs;
      if (state.playerState.item) {
        state.playerState.item.duration_ms = Math.round(durSec * 1000);
      }
    }
    DOM.progressBarFill.style.width = `${pct * 100}%`;

    if (ytAudioPlayer && typeof ytAudioPlayer.seekTo === 'function') {
      ytAudioPlayer.seekTo(newPosSec, true);
    }

    await api('/api/player/seek', {
      method: 'POST',
      body: JSON.stringify({ position_ms: newPosMs })
    }).catch(() => {});
  }

  DOM.progressBarWrap?.addEventListener('mousedown', (e) => {
    if (e.button !== 0) return;
    isScrubbing = true;
    DOM.progressBarFill.style.transition = 'none';
    const pct = getSeekPercentage(e);
    DOM.progressBarFill.style.width = `${pct * 100}%`;
  });

  window.addEventListener('mousemove', (e) => {
    if (!isScrubbing) return;
    const pct = getSeekPercentage(e);
    DOM.progressBarFill.style.width = `${pct * 100}%`;
  });

  window.addEventListener('mouseup', (e) => {
    if (!isScrubbing) return;
    isScrubbing = false;
    DOM.progressBarFill.style.transition = 'width 0.1s linear';
    const pct = getSeekPercentage(e);
    performSeek(pct);
  });

  DOM.progressBarWrap?.addEventListener('touchstart', (e) => {
    if (e.touches.length === 1) {
      isScrubbing = true;
      DOM.progressBarFill.style.transition = 'none';
      const pct = getSeekPercentage(e.touches[0]);
      DOM.progressBarFill.style.width = `${pct * 100}%`;
    }
  }, { passive: true });

  window.addEventListener('touchmove', (e) => {
    if (!isScrubbing || e.touches.length !== 1) return;
    const pct = getSeekPercentage(e.touches[0]);
    DOM.progressBarFill.style.width = `${pct * 100}%`;
  }, { passive: true });

  window.addEventListener('touchend', (e) => {
    if (!isScrubbing) return;
    isScrubbing = false;
    DOM.progressBarFill.style.transition = 'width 0.1s linear';
    const touch = (e.changedTouches && e.changedTouches[0]) ? e.changedTouches[0] : (e.touches && e.touches[0]);
    if (touch) {
      const pct = getSeekPercentage(touch);
      performSeek(pct);
    }
  });

  // Volume Slider
  DOM.volumeSlider?.addEventListener('input', (e) => {
    const vol = parseInt(e.target.value, 10);
    if (ytAudioPlayer && typeof ytAudioPlayer.setVolume === 'function') {
      ytAudioPlayer.setVolume(vol);
    }
    api('/api/player/volume', {
      method: 'POST',
      body: JSON.stringify({ volume_percent: vol })
    }).catch(() => {});
  });

  // Modals
  DOM.closeCreatePlaylistModal?.addEventListener('click', () => DOM.createPlaylistModal.classList.add('hidden'));
  DOM.cancelCreatePlaylistModal?.addEventListener('click', () => DOM.createPlaylistModal.classList.add('hidden'));
  DOM.confirmCreatePlaylistModal?.addEventListener('click', handleCreatePlaylist);

  // Sync Conflict Modal Listeners
  DOM.closeSyncConflictModal?.addEventListener('click', () => DOM.syncConflictModal.classList.add('hidden'));
  DOM.cancelSyncConflictModal?.addEventListener('click', () => DOM.syncConflictModal.classList.add('hidden'));
  DOM.confirmSyncConflictModal?.addEventListener('click', async () => {
    if (!activeSyncPlaylist) return;
    const direction = document.querySelector('input[name="sync-direction"]:checked')?.value || 'app_to_yt';
    DOM.confirmSyncConflictModal.disabled = true;
    try {
      const res = await api(`/api/playlists/${activeSyncPlaylist.id}/sync`, {
        method: 'POST',
        body: JSON.stringify({ direction })
      });
      DOM.syncConflictModal.classList.add('hidden');
      showToast(res.message || 'Synced successfully!');
      await loadPlaylists();
      if (state.activeView === activeSyncPlaylist.id) {
        await loadTracks();
      }
    } catch (e) {
      showToast('Sync error: ' + e.message, 'error');
    } finally {
      DOM.confirmSyncConflictModal.disabled = false;
    }
  });

  // Playlist Context Menu Actions (Export, Sync, Rename, Delete)
  document.getElementById('ctx-playlist-export')?.addEventListener('click', async () => {
    if (!activeContextMenuPlaylist) return;
    const p = activeContextMenuPlaylist;
    const isEs = state.currentLang === 'es';
    showToast(isEs ? `☁️ Exportando "${p.name}" a YouTube Music...` : `☁️ Exporting "${p.name}" to YouTube Music...`, 'info');
    try {
      const res = await api(`/api/playlists/${p.id}/export`, {
        method: 'POST'
      });
      showToast(res.message || (isEs ? '✅ ¡Playlist exportada a YouTube Music!' : '✅ Playlist exported to YouTube Music!'));
      await loadPlaylists();
      if (state.activeView === p.id) {
        await loadTracks();
      }
    } catch (e) {
      showToast('Export error: ' + e.message, 'error');
    }
  });

  document.getElementById('ctx-playlist-sync')?.addEventListener('click', () => {
    if (!activeContextMenuPlaylist) return;
    handleSyncPlaylist(activeContextMenuPlaylist);
  });

  document.getElementById('ctx-playlist-rename')?.addEventListener('click', () => {
    if (!activeContextMenuPlaylist) return;
    const p = activeContextMenuPlaylist;
    showConfirmModal({
      title: `✏️ Rename Playlist`,
      message: `Enter the new name for playlist "${p.name}":`,
      showInput: true,
      inputValue: p.name,
      inputLabel: 'New Playlist Name:',
      confirmText: 'Rename',
      onConfirm: async () => {
        const newName = DOM.confirmModalInput.value.trim();
        if (newName && newName !== p.name) {
          try {
            await api(`/api/playlists/${p.id}/rename`, {
              method: 'POST',
              body: JSON.stringify({ new_name: newName })
            });
            showToast(`✏️ Renamed playlist to "${newName}"`);
            DOM.confirmModal.classList.add('hidden');
            await loadPlaylists();
            if (state.activeView === p.id) {
              loadTracks();
            }
          } catch (e) {
            showToast(e.message, 'error');
          }
        }
      }
    });
  });

  document.getElementById('ctx-playlist-delete')?.addEventListener('click', () => {
    if (!activeContextMenuPlaylist) return;
    const p = activeContextMenuPlaylist;
    showConfirmModal({
      title: `🗑️ Delete Playlist`,
      message: `Are you sure you want to delete playlist "${p.name}"?`,
      confirmText: 'Yes, Delete',
      onConfirm: async () => {
        try {
          await api(`/api/playlists/${p.id}`, { method: 'DELETE' });
          showToast(`🗑️ Deleted playlist "${p.name}"`);
          DOM.confirmModal.classList.add('hidden');
          if (state.activeView === p.id) {
            state.activeView = 'all';
            document.querySelectorAll('.nav-item').forEach(i => {
              if (i.dataset.view === 'all') i.classList.add('active');
              else i.classList.remove('active');
            });
          }
          await loadPlaylists();
          await loadTracks();
        } catch (e) {
          showToast(e.message, 'error');
        }
      }
    });
  });

  // Confirm Modal Cancel & Submit buttons
  DOM.closeConfirmModal?.addEventListener('click', () => DOM.confirmModal.classList.add('hidden'));
  DOM.confirmModalCancelBtn?.addEventListener('click', () => DOM.confirmModal.classList.add('hidden'));
  DOM.confirmModalSubmitBtn?.addEventListener('click', () => {
    if (confirmModalCallback) confirmModalCallback();
  });

  async function triggerLogin() {
    try {
      const isEs = state.currentLang === 'es';
      showToast(isEs ? '🌐 Abriendo ventana de inicio de sesión de Google / YouTube Music...' : '🌐 Opening Google / YouTube Music login window...', 'info');
      await api('/api/auth/login', { method: 'POST' });
      
      // Start polling for successful sign-in
      startAuthPoller();
      
      showToast(isEs 
        ? 'ℹ️ Inicia sesión con tu cuenta de Google en la ventana abierta. La app capturará la sesión automáticamente.' 
        : 'ℹ️ Sign in with your Google account in the opened window. The app will automatically connect once signed in.', 'info', 7000);
    } catch (e) {
      showToast('Login error: ' + e.message, 'error');
    }
  }

  DOM.loginBtn?.addEventListener('click', triggerLogin);
  DOM.modal1ClickLoginBtn?.addEventListener('click', triggerLogin);
  DOM.heroLoginBtn?.addEventListener('click', triggerLogin);

  DOM.saveCookieBtn?.addEventListener('click', async () => {
    const cookie = DOM.settingsCookieInput?.value.trim() || '';
    if (!cookie) {
      showToast(state.currentLang === 'es' ? '⚠️ Por favor ingresa una cookie de sesión válida' : '⚠️ Please enter a valid session cookie', 'warning');
      return;
    }
    try {
      showToast(state.currentLang === 'es' ? 'Guardando sesión y conectando...' : 'Saving session and connecting...', 'info');
      await api('/api/auth/cookie', {
        method: 'POST',
        body: JSON.stringify({ cookie })
      });
      DOM.settingsModal?.classList.add('hidden');
      showToast(state.currentLang === 'es' ? '✅ ¡Conectado! Sincronizando biblioteca...' : '✅ Connected! Syncing library...');
      await checkStatus();
      await triggerAutoSync(false);
    } catch (e) {
      showToast('Connection error: ' + e.message, 'error');
    }
  });

  DOM.logoutBtn?.addEventListener('click', () => {
    showConfirmModal({
      title: '🚪 Log Out & Reset',
      message: 'Are you sure you want to log out? This will disconnect your account and clear the local library cache.',
      confirmText: 'Log Out',
      onConfirm: async () => {
        try {
          await api('/api/auth/logout', { method: 'POST' });
          showToast('🚪 Logged out successfully');
          DOM.confirmModal.classList.add('hidden');
          DOM.settingsModal.classList.add('hidden');
          if (DOM.settingsCookieInput) DOM.settingsCookieInput.value = '';
          state.authenticated = false;
          state.tracks = [];
          state.allPlaylists = [];
          await checkStatus();
          await loadPlaylists();
          await loadTracks();
        } catch (e) {
          showToast(e.message, 'error');
        }
      }
    });
  });

  DOM.settingsBtn?.addEventListener('click', () => DOM.settingsModal.classList.remove('hidden'));
  DOM.closeSettingsModal?.addEventListener('click', () => DOM.settingsModal.classList.add('hidden'));
  DOM.closeSettingsBtnBottom?.addEventListener('click', () => DOM.settingsModal.classList.add('hidden'));

  // Backup Manager Modal Listeners
  DOM.topBackupsBtn?.addEventListener('click', openBackupsModal);
  DOM.closeBackupsModal?.addEventListener('click', () => DOM.backupsModal?.classList.add('hidden'));
  DOM.closeBackupsBtnBottom?.addEventListener('click', () => DOM.backupsModal?.classList.add('hidden'));
  DOM.createManualBackupBtn?.addEventListener('click', handleCreateManualBackup);

  DOM.saveCredentialsBtn?.addEventListener('click', async () => {
    const client_id = DOM.settingsClientId.value.trim();
    const client_secret = DOM.settingsClientSecret.value.trim();
    if (client_id) {
      await api('/api/credentials', {
        method: 'POST',
        body: JSON.stringify({ client_id, client_secret: client_secret || null })
      });
      const auth = await api('/api/auth/login');
      if (auth.auth_url) window.location.href = auth.auth_url;
    }
  });

  document.addEventListener('click', hideAllContextMenus);
  document.getElementById('ctx-play-now')?.addEventListener('click', () => playFromIndex(contextMenuIndex));
  document.getElementById('ctx-toggle-like')?.addEventListener('click', () => {
    const track = state.tracks[contextMenuIndex];
    if (track) handleToggleLikeTrack(track);
  });
  document.getElementById('ctx-true-shuffle')?.addEventListener('click', toggleTrueShuffle);
  document.getElementById('ctx-make-playlist')?.addEventListener('click', () => openCreatePlaylistModal(false));
  document.getElementById('ctx-remove-from-list')?.addEventListener('click', handleRemoveSelectedFromList);
  document.getElementById('ctx-keep-only-selected')?.addEventListener('click', handleKeepOnlySelectedInList);

  // Global Keyboard Shortcuts
  window.addEventListener('keydown', (e) => {
    if (['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement.tagName)) return;

    if (e.code === 'Space') {
      e.preventDefault();
      DOM.ctrlPlaypause?.click();
    } else if (e.key === 'Escape') {
      state.selectedIds.clear();
      updateSelectionUI();
      DOM.settingsModal?.classList.add('hidden');
      DOM.backupsModal?.classList.add('hidden');
      DOM.createPlaylistModal?.classList.add('hidden');
      DOM.confirmModal?.classList.add('hidden');
      hideAllContextMenus();
    } else if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'a') {
      e.preventDefault();
      state.tracks.forEach(t => state.selectedIds.add(t.id));
      updateSelectionUI();
    } else if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'z') {
      e.preventDefault();
      handleUndo();
    } else if (e.key === '/') {
      e.preventDefault();
      DOM.searchInput?.focus();
    }
  });
}

// --- Backup & Restore Manager ---
async function openBackupsModal() {
  DOM.backupsModal?.classList.remove('hidden');
  await loadBackupsList();
}

async function loadBackupsList() {
  if (!DOM.backupsListContainer) return;
  DOM.backupsListContainer.innerHTML = `<div style="text-align: center; color: var(--text-muted); padding: 20px; font-size: 12px;">⏳ Loading snapshots...</div>`;
  try {
    const res = await api('/api/backup/list');
    const backups = res.backups || [];
    renderBackupsList(backups);
  } catch (e) {
    DOM.backupsListContainer.innerHTML = `<div style="color: #ef4444; padding: 16px; text-align: center; font-size: 12px;">❌ Failed to load backups: ${escapeHtml(e.message)}</div>`;
  }
}

function renderBackupsList(backups) {
  if (!DOM.backupsListContainer) return;
  if (!backups || backups.length === 0) {
    DOM.backupsListContainer.innerHTML = `
      <div style="text-align: center; color: var(--text-muted); padding: 24px; font-size: 12px; background: var(--bg-surface-1); border-radius: 6px; border: 1px dashed var(--border-subtle);">
        No snapshots created yet. Click "➕ Create Snapshot Now" above or sync your library to generate one.
      </div>
    `;
    return;
  }

  const isEs = state.currentLang === 'es';
  let html = '';
  backups.forEach(b => {
    let badgeClass = 'backup-badge-manual';
    let badgeLabel = 'MANUAL';
    const reason = b.reason || b.type || '';
    if (reason === 'pre_sync') { badgeClass = 'backup-badge-pre'; badgeLabel = 'PRE-SYNC'; }
    else if (reason === 'post_sync') { badgeClass = 'backup-badge-post'; badgeLabel = 'POST-SYNC'; }
    else if (reason === 'pre_restore_safety') { badgeClass = 'backup-badge-safety'; badgeLabel = 'SAFETY'; }

    const rawBytes = b.file_size_bytes || b.size_bytes || 0;
    const sizeKb = (rawBytes / 1024).toFixed(1);
    const dateFormatted = b.formatted_date || (typeof b.timestamp === 'number' ? new Date(b.timestamp).toLocaleString() : String(b.timestamp || ''));
    const trackCount = b.total_tracks_count ?? b.track_count ?? 0;
    const plCount = b.playlists_count ?? b.playlist_count ?? 0;

    html += `
      <div class="backup-item" data-filename="${escapeHtml(b.filename)}">
        <div class="backup-meta">
          <div style="display: flex; align-items: center; gap: 8px;">
            <span class="backup-badge ${badgeClass}">${badgeLabel}</span>
            <span class="backup-date">${escapeHtml(dateFormatted)}</span>
          </div>
          <div class="backup-details">
            🎵 ${trackCount} tracks · 📑 ${plCount} playlists · 💾 ${sizeKb} KB
          </div>
        </div>
        <div class="backup-actions">
          <button class="backup-btn-restore" title="${isEs ? 'Restaurar esta versión de biblioteca' : 'Restore this library snapshot'}" data-filename="${escapeHtml(b.filename)}">
            ↩️ ${isEs ? 'Restaurar' : 'Restore'}
          </button>
          <button class="backup-btn-dl" title="${isEs ? 'Descargar JSON' : 'Download JSON'}" data-filename="${escapeHtml(b.filename)}">
            ⬇️ JSON
          </button>
          <button class="backup-btn-del" title="${isEs ? 'Eliminar snapshot' : 'Delete snapshot'}" data-filename="${escapeHtml(b.filename)}">
            🗑️
          </button>
        </div>
      </div>
    `;
  });

  DOM.backupsListContainer.innerHTML = html;

  // Attach event listeners to items
  DOM.backupsListContainer.querySelectorAll('.backup-btn-restore').forEach(btn => {
    btn.addEventListener('click', () => {
      const filename = btn.getAttribute('data-filename');
      if (filename) handleRestoreBackup(filename);
    });
  });

  DOM.backupsListContainer.querySelectorAll('.backup-btn-dl').forEach(btn => {
    btn.addEventListener('click', () => {
      const filename = btn.getAttribute('data-filename');
      if (filename) {
        window.open(`/api/backup/file/${encodeURIComponent(filename)}`, '_blank');
      }
    });
  });

  DOM.backupsListContainer.querySelectorAll('.backup-btn-del').forEach(btn => {
    btn.addEventListener('click', () => {
      const filename = btn.getAttribute('data-filename');
      if (filename) handleDeleteBackup(filename);
    });
  });
}

async function handleCreateManualBackup() {
  const isEs = state.currentLang === 'es';
  if (DOM.createManualBackupBtn) DOM.createManualBackupBtn.disabled = true;
  try {
    showToast(isEs ? '💾 Creando copia de seguridad...' : '💾 Creating snapshot...', 'info');
    const res = await api('/api/backup/create', { method: 'POST' });
    showToast(res.message || (isEs ? '✅ Copia creada con éxito' : '✅ Snapshot created successfully'));
    await loadBackupsList();
  } catch (e) {
    showToast('Backup error: ' + e.message, 'error');
  } finally {
    if (DOM.createManualBackupBtn) DOM.createManualBackupBtn.disabled = false;
  }
}

function handleRestoreBackup(filename) {
  const isEs = state.currentLang === 'es';
  showConfirmModal({
    title: isEs ? '↩️ Restaurar Copia de Seguridad' : '↩️ Restore Library Snapshot',
    message: isEs
      ? `¿Estás seguro de que deseas restaurar "${filename}"? Tu estado actual se guardará automáticamente como copia de seguridad de seguridad (safety backup) antes de proceder.`
      : `Are you sure you want to restore "${filename}"? A safety backup of your current state will be generated automatically before restoring.`,
    confirmText: isEs ? 'Sí, Restaurar' : 'Yes, Restore Snapshot',
    onConfirm: async () => {
      try {
        DOM.confirmModal.classList.add('hidden');
        showToast(isEs ? '⏳ Restaurando copia de seguridad...' : '⏳ Restoring snapshot...', 'info');
        const res = await api(`/api/backup/restore/${encodeURIComponent(filename)}`, { method: 'POST' });
        showToast(res.message || (isEs ? '✅ ¡Biblioteca restaurada con éxito!' : '✅ Library restored successfully!'));
        await loadPlaylists();
        await loadTracks();
        await loadBackupsList();
      } catch (e) {
        showToast('Restore error: ' + e.message, 'error');
      }
    }
  });
}

function handleDeleteBackup(filename) {
  const isEs = state.currentLang === 'es';
  showConfirmModal({
    title: isEs ? '🗑️ Eliminar Copia de Seguridad' : '🗑️ Delete Backup Snapshot',
    message: isEs
      ? `¿Estás seguro de que deseas eliminar permanentemente "${filename}"?`
      : `Are you sure you want to permanently delete "${filename}"?`,
    confirmText: isEs ? 'Eliminar' : 'Delete',
    onConfirm: async () => {
      try {
        DOM.confirmModal.classList.add('hidden');
        const res = await api(`/api/backup/file/${encodeURIComponent(filename)}`, { method: 'DELETE' });
        showToast(res.message || (isEs ? '🗑️ Copia eliminada' : '🗑️ Snapshot deleted'));
        await loadBackupsList();
      } catch (e) {
        showToast('Delete error: ' + e.message, 'error');
      }
    }
  });
}

function handleUndo() {
  if (state.undoStack.length > 0) {
    const prevSnapshot = state.undoStack.pop();
    if (Array.isArray(prevSnapshot)) {
      if (prevSnapshot.length === 0) {
        state.tracks = [];
      } else if (typeof prevSnapshot[0] === 'object' && prevSnapshot[0] !== null) {
        state.tracks = [...prevSnapshot];
      } else {
        const map = new Map(state.tracks.map(t => [t.id, t]));
        state.tracks = prevSnapshot.map(id => map.get(id)).filter(Boolean);
      }
      state.userCustomOrderIds = state.tracks.map(t => t.id);
      state.selectedIds.clear();
      renderTracksTable();
      updateSelectionUI();
      showToast('↩️ Restored previous workspace state');
    }
  } else {
    showToast('Nothing to undo');
  }
}

function formatDuration(ms) {
  const totalSecs = Math.floor((ms || 0) / 1000);
  const mins = Math.floor(totalSecs / 60);
  const secs = totalSecs % 60;
  return `${mins}:${secs.toString().padStart(2, '0')}`;
}

function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function normalizeStr(str) {
  return (str || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim();
}

function showToast(message, type = 'info') {
  const toast = document.createElement('div');
  toast.className = 'toast';
  if (type === 'error') toast.style.borderColor = '#ef4444';
  toast.textContent = message;
  DOM.toastContainer.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0';
    setTimeout(() => toast.remove(), 300);
  }, 3500);
}

// --- Sleek Hover & Linger Tooltip System ---
const TooltipManager = {
  tooltipEl: null,
  timer: null,
  lingerDelay: 380, // milliseconds pause before showing tooltip
  currentElem: null,

  init() {
    this.tooltipEl = document.createElement('div');
    this.tooltipEl.id = 'app-tooltip';
    document.body.appendChild(this.tooltipEl);

    // Watch hover with smooth linger delay
    document.addEventListener('mouseover', (e) => {
      const target = e.target.closest('[data-tooltip]');
      if (target) {
        if (target === this.currentElem) return;
        this.clear();
        this.currentElem = target;
        this.timer = setTimeout(() => {
          this.show(target);
        }, this.lingerDelay);
      } else {
        this.clear();
      }
    });

    document.addEventListener('mouseout', (e) => {
      const target = e.target.closest('[data-tooltip]');
      if (target && target === this.currentElem) {
        this.clear();
      }
    });

    document.addEventListener('mousedown', () => {
      this.clear();
    });

    window.addEventListener('scroll', () => this.clear(), true);
  },

  clear() {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    this.currentElem = null;
    if (this.tooltipEl) {
      this.tooltipEl.classList.remove('tooltip-visible');
    }
  },

  show(elem) {
    if (!this.tooltipEl || !elem) return;
    const title = elem.getAttribute('data-tooltip-title') || '';
    const body = elem.getAttribute('data-tooltip') || '';
    const shortcut = elem.getAttribute('data-tooltip-shortcut') || '';

    if (!body && !title) return;

    let html = '';
    if (title || shortcut) {
      html += `<div class="tooltip-header">`;
      if (title) html += `<span class="tooltip-title">${escapeHtml(title)}</span>`;
      if (shortcut) html += `<span class="tooltip-shortcut">${escapeHtml(shortcut)}</span>`;
      html += `</div>`;
    }
    if (body) {
      html += `<div class="tooltip-body">${escapeHtml(body)}</div>`;
    }

    this.tooltipEl.innerHTML = html;
    this.position(elem);
    this.tooltipEl.classList.add('tooltip-visible');
  },

  position(elem) {
    const rect = elem.getBoundingClientRect();
    const tipRect = this.tooltipEl.getBoundingClientRect();
    const pos = elem.getAttribute('data-tooltip-pos') || 'bottom';

    let top = 0;
    let left = 0;

    if (pos === 'top') {
      top = rect.top - tipRect.height - 8;
      left = rect.left + (rect.width - tipRect.width) / 2;
    } else if (pos === 'left') {
      top = rect.top + (rect.height - tipRect.height) / 2;
      left = rect.left - tipRect.width - 8;
    } else if (pos === 'right') {
      top = rect.top + (rect.height - tipRect.height) / 2;
      left = rect.right + 8;
    } else { // default bottom
      top = rect.bottom + 8;
      left = rect.left + (rect.width - tipRect.width) / 2;
    }

    // Viewport boundaries protection
    const pad = 8;
    if (left < pad) left = pad;
    if (left + tipRect.width > window.innerWidth - pad) {
      left = window.innerWidth - tipRect.width - pad;
    }
    if (top < pad) {
      top = rect.bottom + 8;
    }
    if (top + tipRect.height > window.innerHeight - pad) {
      top = rect.top - tipRect.height - 8;
    }

    this.tooltipEl.style.top = `${Math.round(top)}px`;
    this.tooltipEl.style.left = `${Math.round(left)}px`;
  }
};

