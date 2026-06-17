import {
  buildPlaceholder,
  DEFAULT_TEMPLATE,
  TOKEN_DOWNLOAD_TIMESTAMP,
  TOKEN_INDEX,
  TOKEN_INDEX_PADDED,
  TOKEN_POST_ID,
  TOKEN_PUBLISH_TIME,
  TOKEN_TITLE,
  TOKEN_USER_ID,
  TOKEN_USERNAME,
} from './NamingFormat';

/**
 * 文件名模板应用器。把 `{token}` 替换为实际值,并做安全化。
 * 1:1 翻译自 `core/src/commonMain/kotlin/com/xhsdn/core/naming/TemplateApplier.kt`。
 */

export interface TemplateContext {
  fallbackPostId: string;
  mediaIndex: number;
  userName?: string | null;
  userId?: string | null;
  title?: string | null;
  publishTime?: string | null;
  downloadEpochSeconds?: number;
}

export function indexPadded(ctx: TemplateContext): string {
  const v = Math.max(ctx.mediaIndex, 1);
  return v < 10 ? `0${v}` : `${v}`;
}

const NAMING_PLACEHOLDER_PATTERN = /\{([^}]+)\}/g;

/** 把非法字符替换为 `_`,连续空白压成一个,限制长度。 */
export function sanitizeForFilename(value: string | null | undefined, maxLength: number): string | null {
  if (!value) return null;
  let s = value.replace(/[\\/:*?"<>|]/g, '_');
  // 控制字符:Kotlin \p{Cntrl} ≈ JS /[\x00-\x1f\x7f]/g
  s = s.replace(/[\x00-\x1f\x7f]/g, '');
  s = s.trim();
  s = s.replace(/\s+/g, '_');
  s = s.replace(/_+/g, '_');
  s = s.replace(/^_+/, '');
  s = s.replace(/_+$/, '');
  if (maxLength > 0 && s.length > maxLength) s = s.substring(0, maxLength);
  return s.length > 0 ? s : null;
}

function safeTokenValue(value: string | null | undefined, maxLength: number): string | null {
  if (!value) return null;
  const sanitized = sanitizeForFilename(value, 0);
  if (!sanitized) return null;
  if (maxLength > 0 && sanitized.length > maxLength) {
    const available = Math.max(maxLength - 3, 0);
    return available > 0 ? sanitized.substring(0, available) + '...' : sanitized.substring(0, maxLength);
  }
  return sanitized;
}

function resolveTemplateValue(key: string, ctx: TemplateContext, indexPart: string): string | null {
  switch (key) {
    case TOKEN_USERNAME:
      return safeTokenValue(ctx.userName ?? null, 60);
    case TOKEN_USER_ID:
      return safeTokenValue(ctx.userId ?? null, 60);
    case TOKEN_TITLE:
      return safeTokenValue(ctx.title ?? null, 80);
    case TOKEN_POST_ID:
      return safeTokenValue(ctx.fallbackPostId, 60);
    case TOKEN_PUBLISH_TIME:
      return safeTokenValue(ctx.publishTime ?? null, 60);
    case TOKEN_INDEX:
      return `${Math.max(ctx.mediaIndex, 1)}`;
    case TOKEN_INDEX_PADDED:
      return indexPart;
    case TOKEN_DOWNLOAD_TIMESTAMP: {
      const epoch = (ctx.downloadEpochSeconds ?? 0) > 0
        ? ctx.downloadEpochSeconds!
        : Math.floor(Date.now() / 1000);
      return `${epoch}`;
    }
    default:
      return '';
  }
}

function applyTokens(template: string, ctx: TemplateContext, indexPart: string): string {
  NAMING_PLACEHOLDER_PATTERN.lastIndex = 0;
  const sb: string[] = [];
  let lastEnd = 0;
  let m: RegExpExecArray | null;
  while ((m = NAMING_PLACEHOLDER_PATTERN.exec(template)) !== null) {
    sb.push(template.substring(lastEnd, m.index));
    const key = m[1];
    const replacement = resolveTemplateValue(key, ctx, indexPart) ?? '';
    sb.push(replacement);
    lastEnd = m.index + m[0].length;
  }
  sb.push(template.substring(lastEnd));
  return sb.join('');
}

function applyCustomTemplate(
  template: string,
  ctx: TemplateContext,
  indexPart: string,
): string | null {
  const containsIndex =
    template.includes(buildPlaceholder(TOKEN_INDEX)) ||
    template.includes(buildPlaceholder(TOKEN_INDEX_PADDED));
  const containsTitle = template.includes(buildPlaceholder(TOKEN_TITLE));

  const result = containsTitle
    ? applyTokens(
        template.replace(buildPlaceholder(TOKEN_TITLE), safeTokenValue(ctx.title ?? '', 50) ?? ''),
        ctx,
        indexPart,
      )
    : applyTokens(template, ctx, indexPart);

  const sanitized = sanitizeForFilename(result, 0);
  if (!sanitized) return null;
  return containsIndex ? sanitized : `${sanitized}_${indexPart}`;
}

export function buildFileBaseName(
  ctx: TemplateContext,
  customNamingEnabled: boolean,
  customFormatTemplate: string | null = DEFAULT_TEMPLATE,
): string {
  const indexPart = indexPadded(ctx);
  if (customNamingEnabled && customFormatTemplate) {
    const custom = applyCustomTemplate(customFormatTemplate, ctx, indexPart);
    if (custom) return custom;
  }
  return `${ctx.fallbackPostId}_${indexPart}`;
}
