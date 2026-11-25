// ШІ-агент для Ніки.
// Кожен крок: агент читає екран, ШІ обирає дії, агент питає підтвердження і виконує їх.

export const manifest = {
  "id": "ai-agent",
  "name": "Nika AI",
  "version": "1.4",
  "author": "Nika",
  "description": "Виконує задачу на телефоні: ШІ читає екран і по одній дії натискає, вводить текст, прокручує й відкриває програми. Перед кожною дією питає підтвердження. Працює з Claude, Gemini, GPT, Grok, DeepSeek, Mistral, Groq, OpenRouter, власним сервісом і Vivo. Потрібен ключ вибраного сервісу.",
  "permissions": [
    "screen",
    "actions",
    "input",
    "system",
    "speech",
    "network",
    "dialogs"
  ],
  "commands": [
    {
      "id": "agentVoice",
      "title": "Задача голосом (повторно: зупинити)",
      "gesture": "twoFingerDoubleTapAndHold",
      "menu": true
    },
    {
      "id": "agent",
      "title": "Задача текстом (повторно: зупинити)",
      "gesture": "twoFingerTripleTapAndHold",
      "menu": true
    },
    {
      "id": "agentStop",
      "title": "Примусово зупинити агента",
      "gesture": "threeFingerTapAndHold",
      "menu": true
    }
  ],
  "settings": [
    {
      "key": "provider",
      "type": "list",
      "title": "Штучний інтелект",
      "default": "claude",
      "options": [
        {
          "value": "claude",
          "label": "Claude (Anthropic)"
        },
        {
          "value": "gemini",
          "label": "Gemini (Google)"
        },
        {
          "value": "openai",
          "label": "GPT (OpenAI)"
        },
        {
          "value": "grok",
          "label": "Grok (xAI)"
        },
        {
          "value": "deepseek",
          "label": "DeepSeek"
        },
        {
          "value": "mistral",
          "label": "Mistral"
        },
        {
          "value": "groq",
          "label": "Groq"
        },
        {
          "value": "openrouter",
          "label": "OpenRouter (багато моделей з одним ключем)"
        },
        {
          "value": "custom",
          "label": "Свій сервіс (сумісний з OpenAI)"
        },
        {
          "value": "vivo",
          "label": "Vivo BlueLM (через акаунт NVDACN)"
        }
      ]
    },
    {
      "key": "confirmAll",
      "type": "switch",
      "title": "Питати підтвердження перед кожною дією",
      "default": true,
      "summary": "Якщо вимкнути, агент питатиме лише перед небезпечними діями: надсилання, оплата, видалення"
    },
    {
      "key": "voiceConfirm",
      "type": "switch",
      "title": "Підтверджувати голосом, коли задачу дано голосом",
      "default": true,
      "summary": "Скажіть «так» або «ні». Якщо відповідь не розпізнано, з’явиться вікно з кнопками"
    },
    {
      "key": "speakSteps",
      "type": "switch",
      "title": "Озвучувати кроки",
      "default": true
    },
    {
      "key": "history",
      "type": "switch",
      "title": "Пам’ятати останні задачі",
      "default": true,
      "summary": "Агент бачить 3 останні задачі й розуміє продовження"
    },
    {
      "key": "maxSteps",
      "type": "number",
      "title": "Найбільше кроків",
      "default": 30
    },
    {
      "key": "testConnection",
      "type": "button",
      "title": "Перевірити з’єднання з вибраним сервісом"
    },
    {
      "key": "claudeKey",
      "type": "text",
      "title": "Ключ: Claude",
      "summary": "Ключ створюється на console.anthropic.com"
    },
    {
      "key": "claudeModel",
      "type": "text",
      "title": "Модель: Claude",
      "default": "claude-sonnet-5-5",
      "summary": "Назви моделей змінюються, за потреби впишіть актуальну"
    },
    {
      "key": "geminiKey",
      "type": "text",
      "title": "Ключ: Gemini",
      "summary": "Ключ створюється в Google AI Studio: aistudio.google.com"
    },
    {
      "key": "geminiModel",
      "type": "text",
      "title": "Модель: Gemini",
      "default": "gemini-3.8-flash",
      "summary": "Назви моделей змінюються, за потреби впишіть актуальну"
    },
    {
      "key": "openaiKey",
      "type": "text",
      "title": "Ключ: GPT",
      "summary": "Ключ створюється на platform.openai.com"
    },
    {
      "key": "openaiModel",
      "type": "text",
      "title": "Модель: GPT",
      "default": "gpt-5-mini",
      "summary": "Назви моделей змінюються, за потреби впишіть актуальну"
    },
    {
      "key": "grokKey",
      "type": "text",
      "title": "Ключ: Grok",
      "summary": "Ключ створюється на console.x.ai"
    },
    {
      "key": "grokModel",
      "type": "text",
      "title": "Модель: Grok",
      "default": "grok-4",
      "summary": "Назви моделей змінюються, за потреби впишіть актуальну"
    },
    {
      "key": "deepseekKey",
      "type": "text",
      "title": "Ключ: DeepSeek",
      "summary": "Ключ створюється на platform.deepseek.com"
    },
    {
      "key": "deepseekModel",
      "type": "text",
      "title": "Модель: DeepSeek",
      "default": "deepseek-chat",
      "summary": "Назви моделей змінюються, за потреби впишіть актуальну"
    },
    {
      "key": "mistralKey",
      "type": "text",
      "title": "Ключ: Mistral",
      "summary": "Ключ створюється на console.mistral.ai"
    },
    {
      "key": "mistralModel",
      "type": "text",
      "title": "Модель: Mistral",
      "default": "mistral-large-latest",
      "summary": "Назви моделей змінюються, за потреби впишіть актуальну"
    },
    {
      "key": "groqKey",
      "type": "text",
      "title": "Ключ: Groq",
      "summary": "Ключ створюється на console.groq.com"
    },
    {
      "key": "groqModel",
      "type": "text",
      "title": "Модель: Groq",
      "default": "llama-3.3-70b-versatile",
      "summary": "Назви моделей змінюються, за потреби впишіть актуальну"
    },
    {
      "key": "openrouterKey",
      "type": "text",
      "title": "Ключ: OpenRouter",
      "summary": "Ключ створюється на openrouter.ai"
    },
    {
      "key": "openrouterModel",
      "type": "text",
      "title": "Модель: OpenRouter",
      "default": "openrouter/auto",
      "summary": "Назви моделей змінюються, за потреби впишіть актуальну"
    },
    {
      "key": "customBase",
      "type": "text",
      "title": "Свій сервіс: адреса API",
      "summary": "Адреса до /chat/completions, без нього: https://api.example.com/v1"
    },
    {
      "key": "customKey",
      "type": "text",
      "title": "Свій сервіс: ключ"
    },
    {
      "key": "customModel",
      "type": "text",
      "title": "Свій сервіс: модель"
    },
    {
      "key": "nvdacnUser",
      "type": "text",
      "title": "NVDACN: логін",
      "summary": "Акаунт на nvdacn.com, потрібен для Vivo"
    },
    {
      "key": "nvdacnPass",
      "type": "text",
      "title": "NVDACN: пароль"
    },
    {
      "key": "vivoModel",
      "type": "text",
      "title": "Модель Vivo",
      "default": "BlueLM-Vision-Aid"
    }
  ]
};

// ---------------------------------------------------------------- налаштування
const PROVIDER_NAMES = {
  claude: 'Claude', gemini: 'Gemini', openai: 'GPT', grok: 'Grok', deepseek: 'DeepSeek',
  mistral: 'Mistral', groq: 'Groq', openrouter: 'OpenRouter', custom: 'свій сервіс', vivo: 'Vivo',
};
const DEFAULT_MODELS = {
  claude: 'claude-sonnet-5-5', gemini: 'gemini-3.8-flash', openai: 'gpt-5-mini', grok: 'grok-4',
  deepseek: 'deepseek-chat', mistral: 'mistral-large-latest', groq: 'llama-3.3-70b-versatile',
  openrouter: 'openrouter/auto', vivo: 'BlueLM-Vision-Aid',
};
// Сервіси, що приймають запити у форматі OpenAI chat/completions.
const OPENAI_BASES = {
  openai: 'https://api.openai.com/v1',
  grok: 'https://api.x.ai/v1',
  deepseek: 'https://api.deepseek.com',
  mistral: 'https://api.mistral.ai/v1',
  groq: 'https://api.groq.com/openai/v1',
  openrouter: 'https://openrouter.ai/api/v1',
};

const REQUEST_TIMEOUT_MS = 60000;
const MAX_NODES = 400;          // Ніка пам'ятає лише 500 останніх елементів екрана
const MAX_NODES_VIVO = 220;
const MAX_DEPTH = 40;
const MAX_LABEL = 120;
const MAX_LOG = 14;
const MAX_ACTIONS_PER_STEP = 5;
const SCREEN_CHANGING = ['tap', 'long_press', 'scroll', 'back', 'home', 'recents', 'notifications', 'quick_settings', 'open_app'];
const HISTORY_KEY = 'history';
const MAX_HISTORY = 3;
const SENSITIVE_WORDS = [
  'надіслати', 'відправити', 'send', 'оплатити', 'сплатити', 'оплата', 'pay', 'buy', 'купити',
  'замовити', 'order', 'видалити', 'delete', 'remove', 'стерти', 'підтвердити', 'confirm',
  'переказ', 'transfer', 'вийти', 'sign out', 'log out', 'скинути', 'reset', 'форматувати',
];
const KNOWN_APPS = {
  whatsapp: 'com.whatsapp', telegram: 'org.telegram.messenger', viber: 'com.viber.voip',
  chrome: 'com.android.chrome', gmail: 'com.google.android.gm', youtube: 'com.google.android.youtube',
  maps: 'com.google.android.apps.maps', 'play store': 'com.android.vending',
  settings: 'com.android.settings', camera: 'com.android.camera', phone: 'com.google.android.dialer',
  messages: 'com.google.android.apps.messaging', calculator: 'com.google.android.calculator',
  clock: 'com.google.android.deskclock', contacts: 'com.google.android.contacts',
  photos: 'com.google.android.apps.photos', calendar: 'com.google.android.calendar',
};

const setting = (key, fallback) => {
  const value = backtalk.settings.get(key);
  return value === undefined || value === null || value === '' ? fallback : value;
};
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
// ---------------------------------------------------------------- мова телефона
const LANG = (() => {
  let tag = '';
  try { tag = String(backtalk.locale || '').toLowerCase(); } catch (e) { tag = ''; }
  if (tag.indexOf('uk') === 0) return 'uk';
  if (tag.indexOf('ru') === 0 || tag.indexOf('be') === 0) return 'ru';
  return tag ? 'en' : 'uk';
})();
const LANG_NAME = { uk: 'Ukrainian', ru: 'Russian', en: 'the phone language (English if unsure)' }[LANG];
const PHRASES = [
  ["Модель відповіла не в тому форматі. Зупиняюсь. Вона написала:", "Модель ответила не в том формате. Останавливаюсь. Она написала:", "The model answered in the wrong format. Stopping. It wrote:"],
  ["Сервіс відповів, але не в потрібному форматі:", "Сервис ответил, но не в нужном формате:", "The service answered, but not in the needed format:"],
  ["З’єднання з ", "Соединение с ", "Connection to "],
  [" працює", " работает", " works"],
  ["Не вдалося: ", "Не удалось: ", "Failed: "],
  ["Помилка агента: ", "Ошибка агента: ", "Agent error: "],
  ["Помилка: ", "Ошибка: ", "Error: "],
  ["У цій збірці Ніки немає голосового вводу. Введіть задачу текстом.", "В этой сборке Ники нет голосового ввода. Введите задачу текстом.", "This Nika build has no voice input. Type the task."],
  ["У цій збірці Ніки немає голосового вводу", "В этой сборке Ники нет голосового ввода", "This Nika build has no voice input"],
  ["Голосовий ввід не вдався: ", "Голосовой ввод не удался: ", "Voice input failed: "],
  [". Введіть задачу текстом.", ". Введите задачу текстом.", ". Type the task."],
  ["Не почув. Введіть задачу текстом.", "Не расслышал. Введите задачу текстом.", "I did not hear. Type the task."],
  ["Слухаю задачу", "Слушаю задачу", "Listening for the task"],
  ["Зрозумів: ", "Понял: ", "Understood: "],
  ["Що зробити на телефоні?", "Что сделать на телефоне?", "What to do on the phone?"],
  ["Продиктуйте або введіть задачу", "Продиктуйте или введите задачу", "Say or type the task"],
  ["Агента примусово зупинено", "Агент принудительно остановлен", "Agent was force stopped"],
  ["Агент не працював", "Агент не работал", "The agent was not running"],
  ["Зупиняю", "Останавливаю", "Stopping"],
  ["Зупинено", "Остановлено", "Stopped"],
  [" у «", " в «", " into «"],
  ["Прокрутити вгору", "Прокрутить вверх", "Scroll up"],
  ["Прокрутити вниз", "Прокрутить вниз", "Scroll down"],
  ["Дія: назад", "Действие: назад", "Action: back"],
  ["Починаю", "Начинаю", "Starting"],
  ["Ви двічі відмовились. Зупиняюсь.", "Вы дважды отказались. Останавливаюсь.", "You declined twice. Stopping."],
  ["Досягнуто ліміту кроків.", "Достигнут лимит шагов.", "Step limit reached."],
  ["Не зрозумів відповідь", "Не понял ответ", "I did not understand the answer"],
  [" Скажіть так або ні.", " Скажите да или нет.", " Say yes or no."],
  ["Підтвердити дію", "Подтвердить действие", "Confirm the action"],
  ["Відкрити нещодавні програми", "Открыть недавние приложения", "Open recent apps"],
  ["Відкрити швидкі налаштування", "Открыть быстрые настройки", "Open quick settings"],
  ["Відкрити сповіщення", "Открыть уведомления", "Open notifications"],
  ["Відкрити програму ", "Открыть приложение ", "Open app "],
  ["На головний екран", "На главный экран", "Go to the home screen"],
  ["Довго натиснути ", "Долго нажать ", "Long press "],
  ["Натиснути ", "Нажать ", "Tap "],
  ["Ввести текст ", "Ввести текст ", "Type text "],
  ["Прокрутити ", "Прокрутить ", "Scroll "],
  ["елемент ", "элемент ", "item "],
  ["Питання", "Вопрос", "Question"],
  ["Відповідь", "Ответ", "Answer"],
  ["Не вказано ключ", "Не указан ключ", "No key set"],
  ["Не вказано модель", "Не указана модель", "No model set"],
  ["Не вказано адресу свого сервісу", "Не указан адрес своего сервиса", "No address of your own service"],
  ["Не вказано логін і пароль NVDACN", "Не указаны логин и пароль NVDACN", "NVDACN login and password are not set"],
  [" не дав відповіді", " не дал ответа", " gave no answer"],
  [": немає зв’язку (", ": нет связи (", ": no connection ("],
  [": помилка ", ": ошибка ", ": error "],
  [": відповідь не схожа на JSON. ", ": ответ не похож на JSON. ", ": the answer is not JSON. "],
  ["Невідомий сервіс: ", "Неизвестный сервис: ", "Unknown service: "],
  ["відмовлено", "отказано", "refused"],
  ["невідома помилка", "неизвестная ошибка", "unknown error"],
  [" (код ", " (код ", " (code "],
];
const L = (text) => {
  let out = String(text === undefined || text === null ? '' : text);
  if (LANG === 'uk') return out;
  const col = LANG === 'ru' ? 1 : 2;
  for (const row of PHRASES) out = out.split(row[0]).join(row[col]);
  return out;
};

const lower = (text) => String(text === undefined || text === null ? '' : text).toLowerCase();
const clip = (text, limit) => {
  const flat = String(text === undefined || text === null ? '' : text).replace(/\s+/g, ' ').trim();
  return flat.length > limit ? flat.slice(0, limit - 1) + '…' : flat;
};
const say = (text, interrupt) => {
  try { backtalk.speak(L(text), { interrupt: interrupt !== false }); } catch (e) { /* без озвучення */ }
};
const T = (text) => { try { return backtalk._(text); } catch (e) { return text; } };

const state = { running: false, stop: false, plan: '', log: [], voice: false, gen: 0 };

// ---------------------------------------------------------------- читання екрана
async function readScreen(limit) {
  const root = backtalk.screen.root();
  if (!root) return null;
  const entries = [];   // { node, line }
  const lines = [];
  let visited = 0;
  const stack = [{ node: root, depth: 0 }];
  while (stack.length > 0 && visited < limit + 20 && entries.length < limit) {
    const { node, depth } = stack.pop();
    visited += 1;
    if (visited % 40 === 0) await sleep(0);   // на один виклик 2 секунди на один виклик
    if (node.visible === false) continue;
    const label = clip(node.text || node.contentDescription || node.hint || node.stateDescription || '', MAX_LABEL);
    const flags = [];
    if (node.clickable) flags.push('tap');
    if (node.longClickable) flags.push('long');
    if (node.editable) flags.push('edit');
    if (node.scrollable) flags.push('scroll');
    if (node.checkable) flags.push(node.checked ? 'checked' : 'unchecked');
    if (node.enabled === false) flags.push('disabled');
    if (node.focused) flags.push('focused');
    if (node.password) flags.push('password');
    if (label || flags.length > 0) {
      const index = entries.length;
      const shortClass = String(node.className || '').split('.').pop();
      const indent = '  '.repeat(Math.min(depth, 6));
      const line = indent + '[' + index + '] ' + shortClass + (label ? ' "' + label + '"' : '')
        + (flags.length ? ' (' + flags.join(', ') + ')' : '');
      entries.push({ node, label, line });
      lines.push(line);
    }
    if (depth >= MAX_DEPTH) continue;
    let kids = node.children;
    if (typeof kids === 'function') kids = kids.call(node);
    if (!Array.isArray(kids)) {
      kids = [];
      const count = node.childCount || 0;
      for (let i = 0; i < count && i < 100; i += 1) {
        const kid = node.child(i);
        if (kid) kids.push(kid);
      }
    }
    for (let i = kids.length - 1; i >= 0; i -= 1) stack.push({ node: kids[i], depth: depth + 1 });
  }
  const app = backtalk.app || {};
  return { entries, text: lines.join('\n'), app: (app.package || '?') + (app.activity ? ' / ' + app.activity : '') };
}

function signature(screen) {
  if (!screen) return 'none';
  let hash = 0;
  const text = screen.text;
  for (let i = 0; i < text.length; i += 1) hash = (hash * 31 + text.charCodeAt(i)) | 0;
  return screen.entries.length + ':' + hash;
}

async function settle(before) {
  await sleep(500);
  let last = null;
  let stable = 0;
  const started = Date.now();
  while (Date.now() - started < 3500) {
    let screen = null;
    try { screen = await readScreen(120); } catch (e) { screen = null; }
    const now = signature(screen);
    if (now === last) stable += 1; else stable = 0;
    last = now;
    if (stable >= 1 && now !== before) return;
    if (stable >= 2) return;
    await sleep(350);
  }
}

// ---------------------------------------------------------------- запити до сервісів
function uuid() {
  const hex = '0123456789abcdef';
  let out = '';
  for (let i = 0; i < 36; i += 1) {
    if (i === 8 || i === 13 || i === 18 || i === 23) out += '-';
    else if (i === 14) out += '4';
    else if (i === 19) out += hex[(Math.random() * 4) | 8];
    else out += hex[(Math.random() * 16) | 0];
  }
  return out;
}
function nonce(length) {
  const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
  let out = '';
  for (let i = 0; i < length; i += 1) out += chars[(Math.random() * chars.length) | 0];
  return out;
}

async function httpJson(url, options, what) {
  let response = null;
  let raw = '';
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    try {
      response = await fetch(url, Object.assign({ timeout: REQUEST_TIMEOUT_MS }, options));
      raw = await response.text();
    } catch (e) {
      if (attempt === 3) throw new Error(what + ': немає зв’язку (' + (e && e.message ? e.message : e) + ')');
      await sleep(1500 * attempt);
      continue;
    }
    if (response.status === 429 || response.status >= 500) {
      if (attempt === 3) break;
      await sleep(2000 * attempt);
      continue;
    }
    break;
  }
  let parsed = null;
  try { parsed = JSON.parse(raw); } catch (e) { parsed = null; }
  if (!response.ok) {
    let detail = '';
    if (parsed && parsed.error) detail = typeof parsed.error === 'string' ? parsed.error : (parsed.error.message || JSON.stringify(parsed.error));
    else if (parsed && parsed.msg) detail = parsed.msg;
    else detail = clip(raw, 200);
    throw new Error(what + ': помилка ' + response.status + '. ' + clip(detail, 300));
  }
  if (parsed === null) throw new Error(what + ': відповідь не схожа на JSON. ' + clip(raw, 200));
  return parsed;
}

const PROVIDERS = {
  async claude(system, user) {
    const key = setting('claudeKey', '');
    if (!key) throw new Error('Не вказано ключ Claude');
    const data = await httpJson('https://api.anthropic.com/v1/messages', {
      method: 'POST',
      headers: { 'content-type': 'application/json', 'x-api-key': key, 'anthropic-version': '2023-06-01' },
      body: JSON.stringify({
        model: setting('claudeModel', DEFAULT_MODELS.claude), max_tokens: 1500, system,
        messages: [{ role: 'user', content: user }],
      }),
    }, 'Claude');
    const parts = (data.content || []).filter((p) => p.type === 'text').map((p) => p.text);
    return parts.join('\n');
  },
  async gemini(system, user) {
    const key = setting('geminiKey', '');
    if (!key) throw new Error('Не вказано ключ Gemini');
    const model = setting('geminiModel', DEFAULT_MODELS.gemini);
    const data = await httpJson('https://generativelanguage.googleapis.com/v1beta/models/' + encodeURIComponent(model) + ':generateContent', {
      method: 'POST',
      headers: { 'content-type': 'application/json', 'x-goog-api-key': key },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: system }] },
        contents: [{ role: 'user', parts: [{ text: user }] }],
        generationConfig: { temperature: 0, responseMimeType: 'application/json' },
      }),
    }, 'Gemini');
    const candidate = (data.candidates || [])[0];
    if (!candidate || !candidate.content) throw new Error('Gemini не дав відповіді' + (data.promptFeedback ? ': ' + JSON.stringify(data.promptFeedback) : ''));
    return (candidate.content.parts || []).map((p) => p.text || '').join('\n');
  },
  async openaiLike(id, system, user) {
    const custom = id === 'custom';
    const base = String(custom ? setting('customBase', '') : OPENAI_BASES[id]).replace(/\/+$/, '');
    const key = setting(id + 'Key', '');
    const model = setting(id + 'Model', DEFAULT_MODELS[id] || '');
    if (!base) throw new Error('Не вказано адресу свого сервісу');
    if (!key) throw new Error('Не вказано ключ: ' + PROVIDER_NAMES[id]);
    if (!model) throw new Error('Не вказано модель: ' + PROVIDER_NAMES[id]);
    const data = await httpJson(base + '/chat/completions', {
      method: 'POST',
      headers: { 'content-type': 'application/json', authorization: 'Bearer ' + key },
      body: JSON.stringify({
        model,
        messages: [{ role: 'system', content: system }, { role: 'user', content: user }],
      }),
    }, PROVIDER_NAMES[id]);
    const choice = (data.choices || [])[0];
    if (!choice || !choice.message) throw new Error(PROVIDER_NAMES[id] + ' не дав відповіді');
    const content = choice.message.content;
    return Array.isArray(content) ? content.map((p) => p.text || '').join('\n') : String(content || '');
  },
  async vivo(system, user) {
    const account = setting('nvdacnUser', '');
    const password = setting('nvdacnPass', '');
    if (!account || !password) throw new Error('Не вказано логін і пароль NVDACN');
    const appId = '3046775094';
    const uri = '/vivogpt/completions';
    const requestId = uuid();
    const timestamp = String(Math.floor(Date.now() / 1000));
    const nonceText = nonce(8);
    const signed = 'x-ai-gateway-app-id:' + appId + '\nx-ai-gateway-timestamp:' + timestamp + '\nx-ai-gateway-nonce:' + nonceText;
    const signing = 'POST\n' + uri + '\nrequestId=' + requestId + '\n' + appId + '\n' + timestamp + '\n' + signed;
    const signUrl = 'https://nvdacn.com/api/?user=' + encodeURIComponent(account) + '&pass=' + encodeURIComponent(password)
      + '&name=vivo&action=signature';
    const signResult = await httpJson(signUrl, {
      method: 'POST', headers: { 'content-type': 'text/plain' }, body: signing, timeout: 8000,
    }, 'NVDACN');
    if (signResult.code !== 200 || !signResult.data) {
      throw new Error('NVDACN: ' + (typeof signResult.data === 'string' ? signResult.data : 'відмовлено') + ' (код ' + signResult.code + ')');
    }
    const data = await httpJson('https://api-ai.vivo.com.cn' + uri + '?requestId=' + requestId, {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        'X-AI-GATEWAY-APP-ID': appId,
        'X-AI-GATEWAY-TIMESTAMP': timestamp,
        'X-AI-GATEWAY-NONCE': nonceText,
        'X-AI-GATEWAY-SIGNED-HEADERS': 'x-ai-gateway-app-id;x-ai-gateway-timestamp;x-ai-gateway-nonce',
        'X-AI-GATEWAY-SIGNATURE': String(signResult.data),
      },
      body: JSON.stringify({
        model: setting('vivoModel', DEFAULT_MODELS.vivo),
        sessionId: uuid(),
        provider: 'vivo',
        messages: [{ role: 'user', content: system + '\n\n' + user, contentType: 'text' }],
        extra: { temperature: 0, max_tokens: 1024 },
      }),
    }, 'Vivo');
    if (data.code !== undefined && data.code !== 0) throw new Error('Vivo: ' + (data.msg || 'невідома помилка') + ' (код ' + data.code + ')');
    const content = data.data && data.data.content;
    if (!content) throw new Error('Vivo не дав відповіді');
    return String(content);
  },
};

async function askModel(system, user) {
  const id = String(setting('provider', 'claude'));
  if (id === 'claude' || id === 'gemini' || id === 'vivo') return PROVIDERS[id](system, user);
  if (id === 'custom' || OPENAI_BASES[id]) return PROVIDERS.openaiLike(id, system, user);
  throw new Error('Невідомий сервіс: ' + id);
}

// ---------------------------------------------------------------- промпт і розбір відповіді
const SYSTEM_PROMPT = [
  'You operate an Android phone for a blind user through its accessibility tree. Each step you receive the task, your plan, a log of earlier actions and the current screen as a numbered list of elements.',
  'The phone language is ' + LANG_NAME + '. Write "say", "summary" and "question" in that language unless the task is clearly in another one.',
  'Answer ONLY with one JSON object, no other text, no code fences:',
  '{"plan": "<your short plan, updated each step>", "say": "<one short sentence for the user, in the user language, telling what you do now>", "actions": [ ... ]}',
  'Each action is an object with "tool" and its arguments. Tools:',
  '- {"tool":"tap","index":N}  press element N',
  '- {"tool":"long_press","index":N}',
  '- {"tool":"type_text","index":N,"text":"...","append":false}  set the text of the edit field N (replaces the text unless append is true)',
  '- {"tool":"scroll","index":N,"direction":"down"|"up"}  scroll element N (omit index to scroll the first scrollable element)',
  '- {"tool":"back"}, {"tool":"home"}, {"tool":"recents"}, {"tool":"notifications"}, {"tool":"quick_settings"}',
  '- {"tool":"open_app","package":"com.example.app"}  open an app by its package name',
  '- {"tool":"wait","ms":1000}',
  '- {"tool":"ask_user","question":"..."}  ask the user when you need a choice or information; the answer comes back in the log',
  '- {"tool":"finish","summary":"..."}  the task is done or cannot be done; summary is a short sentence in the user language',
  'Rules:',
  '1. Give 1 to ' + MAX_ACTIONS_PER_STEP + ' actions. Use several only when the screen will not change between them. After a tap that changes the screen give no more actions in that step.',
  '2. Use only element numbers from the current screen list. Numbers change every step.',
  '3. Flags in brackets: tap = clickable, edit = text field, scroll = scrollable, long = long-clickable. Text in quotes is the element label.',
  '4. If the screen did not change after your action, do not repeat it: try another element, scroll, or ask the user.',
  '5. Never enter passwords, card numbers or codes yourself: use ask_user. Never invent data.',
  '6. Finish with "finish" only when the screen confirms the task is done. Everything you say is in the language of the task.',
  '7. Known package names: ' + Object.keys(KNOWN_APPS).map((name) => name + '=' + KNOWN_APPS[name]).join(', ') + '.',
].join('\n');

function buildUserText(task, screen, step, maxSteps, history) {
  const parts = [];
  parts.push('Task: ' + task);
  if (history.length) parts.push('Earlier tasks (newest last):\n' + history.map((h) => '- ' + h).join('\n'));
  parts.push('Step ' + step + ' of at most ' + maxSteps + '.');
  parts.push('Your plan so far: ' + (state.plan || 'none yet'));
  parts.push('Log of recent actions:\n' + (state.log.length ? state.log.slice(-MAX_LOG).join('\n') : 'none yet'));
  parts.push('Front app: ' + (screen ? screen.app : 'unknown'));
  parts.push('Screen:\n' + (screen && screen.text ? screen.text : '(empty, the screen could not be read)'));
  return parts.join('\n\n');
}

function tryParse(chunk) {
  const variants = [chunk];
  const fixed = chunk
    .replace(/[\u201c\u201d\u201e\u00ab\u00bb]/g, '"')
    .replace(/[\u2018\u2019]/g, "'")
    .replace(/,\s*([}\]])/g, '$1');
  variants.push(fixed);
  for (const v of variants) {
    try { return JSON.parse(v); } catch (e) { /* next */ }
  }
  return null;
}

function balancedAt(body, start) {
  const open = body[start];
  const close = open === '{' ? '}' : ']';
  let depth = 0;
  let inString = false;
  let escaped = false;
  for (let i = start; i < body.length; i += 1) {
    const ch = body[i];
    if (inString) {
      if (escaped) escaped = false;
      else if (ch === '\\') escaped = true;
      else if (ch === '"') inString = false;
      continue;
    }
    if (ch === '"') inString = true;
    else if (ch === open) depth += 1;
    else if (ch === close) {
      depth -= 1;
      if (depth === 0) return body.slice(start, i + 1);
    }
  }
  return null;
}

function extractJson(text) {
  const body = String(text || '').replace(/```(?:json)?/gi, ' ').trim();
  let fallback = null;
  for (let i = 0; i < body.length; i += 1) {
    if (body[i] !== '{' && body[i] !== '[') continue;
    const chunk = balancedAt(body, i);
    if (!chunk) continue;
    let parsed = tryParse(chunk);
    if (Array.isArray(parsed)) parsed = { actions: parsed };
    if (parsed && typeof parsed === 'object') {
      if (Array.isArray(parsed.actions)) return parsed;
      if (!fallback && typeof parsed.tool === 'string') fallback = { actions: [parsed] };
    }
  }
  return fallback;
}

// ---------------------------------------------------------------- голос
const YES_WORDS = ['так', 'да', 'yes', 'yeah', 'ok', 'окей', 'добре', 'давай', 'виконуй', 'виконати', 'підтверджую', 'звісно', 'ага', 'угу', 'згоден'];
const NO_WORDS = ['ні', 'нет', 'no', 'не', 'стоп', 'stop', 'відміна', 'скасувати', 'зупинись', 'відмінити', 'неа'];

const voiceAvailable = () => !!(backtalk.voice && typeof backtalk.voice.listen === 'function');

function beep() {
  try { backtalk.playTone(880, 120); } catch (e) { /* без звуку */ }
}

// Нічого не слухаємо, поки Ніка ще говорить: інакше розпізнавач почує її голос.
const speechPause = (text) => Math.min(700 + 60 * String(text || '').length, 6000);

async function listen(prompt, seconds) {
  if (!voiceAvailable()) throw new Error('У цій збірці Ніки немає голосового вводу');
  if (prompt) {
    say(prompt);
    await sleep(speechPause(prompt));
  }
  beep();
  await sleep(150);
  const heard = await backtalk.voice.listen({ seconds });
  return heard ? String(heard).trim() : null;
}

function answerOf(text) {
  const words = lower(text).split(/[^\p{L}]+/u).filter(Boolean);
  const yes = words.some((w) => YES_WORDS.includes(w));
  const no = words.some((w) => NO_WORDS.includes(w));
  if (yes && !no) return true;
  if (no && !yes) return false;
  return null;
}

// ---------------------------------------------------------------- підтвердження і виконання дій
function labelOf(entry) { return entry ? entry.label : ''; }

function describe(call, screen) {
  const entry = screen && call.index !== undefined ? screen.entries[call.index] : null;
  const name = labelOf(entry);
  const target = name ? '«' + name + '»' : (call.index !== undefined ? 'елемент ' + call.index : '');
  switch (call.tool) {
    case 'tap': return 'Натиснути ' + target;
    case 'long_press': return 'Довго натиснути ' + target;
    case 'type_text': return 'Ввести текст «' + clip(call.text, 80) + '»' + (target ? ' у ' + target : '');
    case 'scroll': return (call.direction === 'up' ? 'Прокрутити вгору' : 'Прокрутити вниз');
    case 'back': return 'Дія: назад';
    case 'home': return 'На головний екран';
    case 'recents': return 'Відкрити нещодавні програми';
    case 'notifications': return 'Відкрити сповіщення';
    case 'quick_settings': return 'Відкрити швидкі налаштування';
    case 'open_app': return 'Відкрити програму ' + clip(call.package, 60);
    default: return call.tool;
  }
}

function needsConfirmation(call, screen) {
  if (call.tool === 'wait' || call.tool === 'finish' || call.tool === 'ask_user') return false;
  if (setting('confirmAll', true)) return true;
  const entry = screen && call.index !== undefined ? screen.entries[call.index] : null;
  const text = lower(labelOf(entry) + ' ' + (call.text || '') + ' ' + (call.package || ''));
  return SENSITIVE_WORDS.some((word) => text.includes(word));
}

async function confirmCall(call, screen) {
  const question = describe(call, screen) + '?';
  if (state.voice && setting('voiceConfirm', true) && voiceAvailable()) {
    try {
      const heard = await listen(question + ' Скажіть так або ні.', 6);
      const verdict = heard ? answerOf(heard) : null;
      if (verdict !== null) return verdict;
      say('Не зрозумів відповідь');
    } catch (e) {
      console.warn('Voice confirmation failed: ' + (e && e.message ? e.message : e));
    }
  } else {
    say(question);
  }
  const ok = await backtalk.ui.confirm(L(question), { title: L('Підтвердити дію'), ok: { uk: 'Так', ru: 'Да', en: 'Yes' }[LANG], cancel: { uk: 'Ні', ru: 'Нет', en: 'No' }[LANG] });
  await sleep(400);   // діалог закривається, екран застосунку знову активний
  return ok === true;
}

function clickableAncestor(node) {
  let current = node;
  for (let i = 0; i < 4 && current; i += 1) {
    if (current.clickable) return current;
    let up = current.parent;
    if (typeof up === 'function') up = up.call(current);
    current = up;
  }
  return node;
}

async function performCall(call, screen) {
  const entries = screen ? screen.entries : [];
  const entry = call.index !== undefined ? entries[call.index] : null;
  const needsNode = ['tap', 'long_press', 'type_text'].includes(call.tool);
  if (needsNode && !entry) return 'failed: there is no element ' + call.index;
  switch (call.tool) {
    case 'tap': {
      const node = clickableAncestor(entry.node);
      return (await node.click()) ? 'done' : 'failed: the element did not react';
    }
    case 'long_press': {
      return (await entry.node.longClick()) ? 'done' : 'failed: the element did not react';
    }
    case 'type_text': {
      let text = String(call.text === undefined ? '' : call.text);
      if (call.append && entry.node.text) text = entry.node.text + text;
      return (await entry.node.setText(text)) ? 'done' : 'failed: could not set the text';
    }
    case 'scroll': {
      let target = entry;
      if (!target) target = entries.find((item) => item.node.scrollable);
      if (!target) return 'failed: nothing to scroll';
      const forward = call.direction !== 'up' && call.direction !== 'back';
      const worked = forward ? await target.node.scrollForward() : await target.node.scrollBackward();
      return worked ? 'done' : 'failed: cannot scroll further';
    }
    case 'back': backtalk.system.back(); return 'done';
    case 'home': backtalk.system.home(); return 'done';
    case 'recents': backtalk.system.recents(); return 'done';
    case 'notifications': backtalk.system.notifications(); return 'done';
    case 'quick_settings': backtalk.system.quickSettings(); return 'done';
    case 'open_app': {
      const wanted = String(call.package || '').trim();
      const pkg = KNOWN_APPS[lower(wanted)] || wanted;
      if (!pkg) return 'failed: no package name';
      return (await backtalk.system.openApp(pkg)) ? 'done' : 'failed: the app was not found or did not open';
    }
    case 'wait': await sleep(Math.min(Math.max(Number(call.ms) || 1000, 200), 5000)); return 'done';
    default: return 'failed: unknown tool ' + call.tool;
  }
}

function addLog(text) {
  state.log.push(text);
  if (state.log.length > 40) state.log.splice(0, state.log.length - 40);
}

// ---------------------------------------------------------------- головний цикл
async function runTask(task) {
  const myGen = state.gen;
  const halted = () => state.stop || myGen !== state.gen;
  const maxSteps = Math.max(1, Math.min(Number(setting('maxSteps', 30)) || 30, 100));
  const history = setting('history', true) ? (backtalk.storage.get(HISTORY_KEY, []) || []) : [];
  const limit = String(setting('provider', 'claude')) === 'vivo' ? MAX_NODES_VIVO : MAX_NODES;
  state.plan = '';
  state.log = [];
  let outcome = 'зупинено';
  let declinedInRow = 0;
  let badAnswers = 0;
  for (let step = 1; step <= maxSteps; step += 1) {
    if (halted()) break;
    let screen = null;
    try { screen = await readScreen(limit); } catch (e) {
      addLog('could not read the screen: ' + (e && e.message ? e.message : e));
    }
    let reply = '';
    try {
      reply = await askModel(SYSTEM_PROMPT, buildUserText(task, screen, step, maxSteps, history));
    } catch (e) {
      say('Помилка: ' + (e && e.message ? e.message : e));
      outcome = 'помилка: ' + (e && e.message ? e.message : e);
      break;
    }
    if (halted()) break;
    const decision = extractJson(reply);
    if (!decision || !Array.isArray(decision.actions)) {
      badAnswers += 1;
      addLog('your last answer was not valid JSON with an "actions" list; answer only with the JSON object, nothing before or after it. Your answer was: ' + clip(reply, 300));
      if (badAnswers >= 3) { outcome = 'модель відповіла не в тому форматі'; say('Модель відповіла не в тому форматі. Зупиняюсь. Вона написала: ' + clip(reply, 150)); break; }
      continue;
    }
    badAnswers = 0;
    if (typeof decision.plan === 'string') state.plan = clip(decision.plan, 800);
    if (decision.say && setting('speakSteps', true)) say(clip(decision.say, 300), false);
    const before = signature(screen);
    let finished = false;
    for (const call of decision.actions.slice(0, MAX_ACTIONS_PER_STEP)) {
      if (halted() || !call || typeof call.tool !== 'string') break;
      if (call.tool === 'finish') {
        outcome = String(call.summary || 'готово');
        say(outcome);
        finished = true;
        break;
      }
      if (call.tool === 'ask_user') {
        let answer = null;
        let asked = false;
        if (state.voice && voiceAvailable()) {
          try {
            answer = await listen(String(call.question || ''), 12);
            asked = answer !== null;
          } catch (e) {
            console.warn('Voice answer failed: ' + (e && e.message ? e.message : e));
          }
        }
        if (!asked) {
          say(String(call.question || ''));
          answer = await backtalk.ui.prompt(L(String(call.question || 'Питання')), { hint: L('Відповідь') });
          await sleep(400);
        }
        addLog('asked the user "' + clip(call.question, 200) + '" and the answer was: ' + (answer === null ? '(cancelled)' : answer));
        if (answer === null) { state.stop = true; outcome = 'ви скасували питання'; }
        break;
      }
      if (needsConfirmation(call, screen)) {
        const allowed = await confirmCall(call, screen);
        if (!allowed) {
          declinedInRow += 1;
          addLog('the user declined: ' + describe(call, screen));
          break;
        }
        declinedInRow = 0;
      }
      let result = '';
      try { result = await performCall(call, screen); } catch (e) { result = 'failed: ' + (e && e.message ? e.message : e); }
      addLog('step ' + step + ': ' + describe(call, screen) + ' -> ' + result);
      if (result !== 'done') break;
      if (SCREEN_CHANGING.includes(call.tool)) {
        // Екран змінився: наступні дії цього кроку вже не відповідають номерам, модель отримає новий екран.
        await settle(before);
        break;
      }
      await sleep(300);   // введення тексту й очікування не міняють номерів елементів
    }
    if (finished) break;
    if (declinedInRow >= 2) { outcome = 'ви двічі відхилили дії'; say('Ви двічі відмовились. Зупиняюсь.'); break; }
    if (step === maxSteps) { outcome = 'досягнуто ліміту кроків'; say('Досягнуто ліміту кроків.'); }
  }
  if (setting('history', true)) {
    const saved = backtalk.storage.get(HISTORY_KEY, []) || [];
    saved.push(clip(task, 200) + ' → ' + clip(outcome, 200));
    backtalk.storage.set(HISTORY_KEY, saved.slice(-MAX_HISTORY));
  }
  return outcome;
}

async function askTask(byVoice) {
  if (byVoice) {
    if (!voiceAvailable()) {
      say('У цій збірці Ніки немає голосового вводу. Введіть задачу текстом.');
    } else {
      try {
        const heard = await listen('Слухаю задачу', 15);
        if (heard) {
          state.voice = true;
          say('Зрозумів: ' + clip(heard, 200), true);
          await sleep(speechPause('Зрозумів: ' + clip(heard, 200)));
          return heard;
        }
        say('Не почув. Введіть задачу текстом.');
      } catch (e) {
        say('Голосовий ввід не вдався: ' + (e && e.message ? e.message : e) + '. Введіть задачу текстом.');
      }
    }
  }
  const typed = await backtalk.ui.prompt(L('Що зробити на телефоні?'), { hint: L('Продиктуйте або введіть задачу') });
  await sleep(400);
  return typed;
}

async function startAgent(byVoice) {
  const myGen = state.gen;
  try {
    state.voice = false;
    const task = await askTask(byVoice);
    if (myGen !== state.gen) return;
    if (state.stop) { say('Зупинено', true); return; }
    if (!task || !String(task).trim()) { return; }
    say('Починаю');
    const outcome = await runTask(String(task).trim());
    if (myGen !== state.gen) return;
    if (state.stop) say('Зупинено', true);
    else if (outcome.indexOf('помилка') === 0) { /* вже озвучено */ }
  } catch (e) {
    say('Помилка агента: ' + (e && e.message ? e.message : e));
    console.error(e && e.stack ? e.stack : String(e));
  } finally {
    if (myGen === state.gen) {
      state.running = false;
      state.stop = false;
      state.voice = false;
    }
  }
}

const toggle = (byVoice) => {
  if (state.running) {
    state.stop = true;
    say('Зупиняю');
    return;
  }
  state.running = true;
  state.stop = false;
  setTimeout(() => startAgent(byVoice), 0);
};
backtalk.onCommand('agent', () => toggle(false));
backtalk.onCommand('agentVoice', () => toggle(true));
// Примусова зупинка: старий запуск відкидається (навіть якщо він чекає відповіді), стан скидається.
backtalk.onCommand('agentStop', () => {
  const was = state.running;
  state.gen += 1;
  state.stop = false;
  state.running = false;
  state.voice = false;
  say(was ? 'Агента примусово зупинено' : 'Агент не працював', true);
});

backtalk.onButton((key) => {
  if (key !== 'testConnection') return;
  setTimeout(async () => {
    const id = String(setting('provider', 'claude'));
    try {
      const reply = await askModel('Answer only with this JSON object: {"ok": true}', 'Reply now.');
      const parsed = extractJson(reply);
      say(parsed ? 'З’єднання з ' + PROVIDER_NAMES[id] + ' працює' : 'Сервіс відповів, але не в потрібному форматі: ' + clip(reply, 120));
    } catch (e) {
      say('Не вдалося: ' + (e && e.message ? e.message : e));
    }
  }, 0);
});
