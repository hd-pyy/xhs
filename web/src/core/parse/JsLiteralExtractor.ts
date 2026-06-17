/**
 * JS 对象字面量提取与 `undefined` → `null` 转换工具。
 *
 * 1:1 翻译自 `core/src/commonMain/kotlin/com/xhsdn/core/parse/JsLiteralExtractor.kt`。
 * 用法:HTML 拿到 `window.__INITIAL_STATE__ = {...} ; foo();` 时,
 *   先调 [extractFirstJsObjectLiteral] 切出第一个完整对象,
 *   再调 [replaceJsUndefinedWithNull] 把裸 `undefined` 替换为 `null`,
 *   最后送入 JSON 解析。
 */

/**
 * 从一段 JS 片段中提取第一个平衡的 `{...}` 对象字面量(跳过字符串内的花括号)。
 * 返回包含花括号本身的子串。
 */
export function extractFirstJsObjectLiteral(jsSnippet: string | null | undefined): string | null {
  if (!jsSnippet) return null;
  let inString = false;
  let quote = ' ';
  let escape = false;
  let depth = 0;
  let start = -1;

  for (let i = 0; i < jsSnippet.length; i++) {
    const c = jsSnippet[i];
    if (inString) {
      if (escape) {
        escape = false;
        continue;
      }
      if (c === '\\') {
        escape = true;
        continue;
      }
      if (c === quote) {
        inString = false;
        quote = ' ';
      }
      continue;
    }
    if (c === '"' || c === "'") {
      inString = true;
      quote = c;
      continue;
    }
    if (c === '{') {
      if (depth === 0) start = i;
      depth++;
    } else if (c === '}') {
      if (depth > 0) {
        depth--;
        if (depth === 0 && start !== -1) {
          return jsSnippet.substring(start, i + 1);
        }
      }
    }
  }
  return null;
}

function isJsIdentifierChar(c: string): boolean {
  if (!c) return false;
  return /[A-Za-z0-9_$]/.test(c);
}

/**
 * 把裸 `undefined` 替换为 `null`(仅在字符串外、作为独立 token 时替换)。
 */
export function replaceJsUndefinedWithNull(input: string | null | undefined): string {
  if (!input || !input.includes('undefined')) return input ?? '';
  const out: string[] = [];
  let inString = false;
  let quote = ' ';
  let escape = false;

  let i = 0;
  while (i < input.length) {
    const c = input[i];
    if (inString) {
      out.push(c);
      if (escape) {
        escape = false;
        i++;
        continue;
      }
      if (c === '\\') {
        escape = true;
      } else if (c === quote) {
        inString = false;
        quote = ' ';
      }
      i++;
      continue;
    }
    if (c === '"' || c === "'") {
      inString = true;
      quote = c;
      out.push(c);
      i++;
      continue;
    }
    const kw = 'undefined';
    if (i + kw.length <= input.length && input.substring(i, i + kw.length) === kw) {
      const prev = i > 0 ? input[i - 1] : ' ';
      const next = i + kw.length < input.length ? input[i + kw.length] : ' ';
      const prevOk = i === 0 || !isJsIdentifierChar(prev);
      const nextOk = i + kw.length === input.length || !isJsIdentifierChar(next);
      if (prevOk && nextOk) {
        out.push('null');
        i += kw.length;
        continue;
      }
    }
    out.push(c);
    i++;
  }
  return out.join('');
}
