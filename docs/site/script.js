'use strict';
const copy = document.getElementById('copy-checksum');
const status = document.getElementById('copy-status');
copy.addEventListener('click', async () => {
  const value = document.getElementById('checksum-value').textContent.trim();
  try {
    await navigator.clipboard.writeText(value);
    status.textContent = '已复制';
  } catch {
    const range = document.createRange();
    range.selectNodeContents(document.getElementById('checksum-value'));
    const selection = window.getSelection();
    selection.removeAllRanges(); selection.addRange(range);
    status.textContent = '请复制已选中的校验值';
  }
});
