/**
 * 命名模板占位符常量与默认模板。
 * 1:1 翻译自 `core/src/commonMain/kotlin/com/xhsdn/core/naming/NamingFormat.kt`。
 */

export const TOKEN_USERNAME = 'username';
export const TOKEN_USER_ID = 'user_id';
export const TOKEN_TITLE = 'title';
export const TOKEN_POST_ID = 'post_id';
export const TOKEN_PUBLISH_TIME = 'publish_time';
export const TOKEN_INDEX = 'index';
export const TOKEN_INDEX_PADDED = 'index_padded';
export const TOKEN_DOWNLOAD_TIMESTAMP = 'download_timestamp';

export const DEFAULT_TEMPLATE = `{${TOKEN_POST_ID}}_{${TOKEN_INDEX_PADDED}}`;

export function buildPlaceholder(token: string): string {
  return `{${token}}`;
}
