import { extractFirstJsObjectLiteral, replaceJsUndefinedWithNull } from './JsLiteralExtractor';

/**
 * 从 HTML 中提取 `window.__INITIAL_STATE__ = {...}` 并解析为 JSON 对象。
 *
 * 1:1 翻译自 `core/src/commonMain/kotlin/com/xhsdn/core/parse/InitialStateParser.kt`,
 * 但用原生 `JSON.parse` 替代 kotlinx-serialization。
 */
export function parse(html: string | null | undefined): Record<string, unknown> | null {
  if (!html) return null;
  const startIndex = html.indexOf('window.__INITIAL_STATE__');
  if (startIndex < 0) return null;
  const endIndex = html.indexOf('</script>', startIndex);
  if (endIndex < 0) return null;

  const scriptContent = html.substring(startIndex, endIndex);
  const equalsIndex = scriptContent.indexOf('=');
  if (equalsIndex < 0) return null;

  const afterEquals = scriptContent.substring(equalsIndex + 1).trim();
  const jsObject = extractFirstJsObjectLiteral(afterEquals) ?? afterEquals;
  const cleaned = replaceJsUndefinedWithNull(jsObject.trim().replace(/;$/, ''));

  try {
    return JSON.parse(cleaned);
  } catch {
    return null;
  }
}
