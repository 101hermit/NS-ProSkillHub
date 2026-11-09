/**
 * 关键词高亮：把一段文本切成「普通片段 / 命中片段」，交给模板用 <mark> 渲染。
 *
 * <p><b>为什么不在前端用 v-html 拼 {@code <mark>}</b>：Skill 的名称、标题、描述来自
 * 用户提交与外部分发，直接当 HTML 插进页面就是给自己开一个 XSS 口子。
 * 切片渲染没有这个问题 —— 命中片段仍然是纯文本，只是外面多了一个标签。
 *
 * <p>为什么要做这件事：资源站的核心动作是"找到它"。只说"找到了"却不告诉用户
 * "命中在哪个字段"，用户就得自己在一屏文字里找 —— 这是检索页最常见的浪费。
 */

/** 把关键词规整成"用于比较"的形式（大小写无关、去首尾空白）。 */
function norm(s) {
  return String(s ?? '').trim().toLowerCase();
}

/**
 * 切分文本。
 *
 * @param {string} text 原文
 * @param {string} kw   关键词（空则原样返回一段）
 * @returns {Array<{text: string, hit: boolean}>}
 */
export function segments(text, kw) {
  const src = String(text ?? '');
  const needle = norm(kw);
  if (!needle) {
    return [{ text: src, hit: false }];
  }
  const hay = src.toLowerCase();
  const out = [];
  let from = 0;
  for (;;) {
    const at = hay.indexOf(needle, from);
    if (at < 0) {
      break;
    }
    if (at > from) {
      out.push({ text: src.slice(from, at), hit: false });
    }
    out.push({ text: src.slice(at, at + needle.length), hit: true });
    from = at + needle.length;
  }
  if (from < src.length) {
    out.push({ text: src.slice(from), hit: false });
  }
  // 关键词比原文还长、或压根没有命中时，至少要把原文显示出来
  return out.length ? out : [{ text: src, hit: false }];
}

/** 这段文本是否命中了关键词。 */
export function hasHit(text, kw) {
  const needle = norm(kw);
  return !!needle && norm(text).includes(needle);
}

/**
 * 命中了卡片上的哪几个字段。
 *
 * <p>字段范围必须与后端 {@code skills?kw=} 的过滤条件**完全一致**
 * （name / title / description / tags 四列）。多报一个"命中正文"是撒谎，
 * 少报一个会让用户以为结果不相关 —— 这里的顺序就是页面上的显示顺序。
 */
export function matchedFields(card, kw) {
  if (!card || !norm(kw)) {
    return [];
  }
  const fields = [
    ['名称', card.name],
    ['标题', card.title],
    ['描述', card.description],
    ['标签', Array.isArray(card.tags) ? card.tags.join(' ') : card.tags],
  ];
  return fields.filter(([, v]) => hasHit(v, kw)).map(([k]) => k);
}