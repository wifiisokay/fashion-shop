import { useState, useCallback, useRef } from 'react';
import ReactMarkdown from 'react-markdown';
import { 
  Bold, Italic, List, ListOrdered, Heading2, Heading3, 
  Link, Eye, Pencil, Quote, Minus, Code, Strikethrough
} from 'lucide-react';
import remarkGfm from 'remark-gfm';
import '../../styles/markdown-editor.css';

/**
 * Markdown editor với toolbar + live preview.
 * Admin dùng để nhập mô tả sản phẩm có format.
 */
const MarkdownEditor = ({ value = '', onChange, placeholder = 'Nhập nội dung...' }) => {
  const [mode, setMode] = useState('edit'); // 'edit' | 'preview'
  const textareaRef = useRef(null);

  // Insert markdown syntax quanh text đang select
  const wrapSelection = useCallback((before, after = before) => {
    const el = textareaRef.current;
    if (!el) return;
    const start = el.selectionStart;
    const end = el.selectionEnd;
    const text = value || '';
    const selected = text.substring(start, end);
    const newText = text.substring(0, start) + before + selected + after + text.substring(end);
    onChange(newText);
    requestAnimationFrame(() => {
      el.focus();
      el.selectionStart = start + before.length;
      el.selectionEnd = end + before.length;
    });
  }, [value, onChange]);

  // Insert at cursor (for line-level items)
  const insertAtLineStart = useCallback((prefix) => {
    const el = textareaRef.current;
    if (!el) return;
    const start = el.selectionStart;
    const text = value || '';
    const lineStart = text.lastIndexOf('\n', start - 1) + 1;
    const newText = text.substring(0, lineStart) + prefix + text.substring(lineStart);
    onChange(newText);
    requestAnimationFrame(() => {
      el.focus();
      el.selectionStart = el.selectionEnd = start + prefix.length;
    });
  }, [value, onChange]);

  const toolsDef = [
    { icon: Bold, title: 'In đậm', id: 'bold' },
    { icon: Italic, title: 'In nghiêng', id: 'italic' },
    { icon: Code, title: 'Code', id: 'code' },
    'sep',
    { icon: Heading2, title: 'Heading 2', id: 'h2' },
    { icon: Heading3, title: 'Heading 3', id: 'h3' },
    'sep',
    { icon: List, title: 'Danh sách', id: 'ul' },
    { icon: ListOrdered, title: 'Đánh số', id: 'ol' },
    { icon: Quote, title: 'Trích dẫn', id: 'quote' },
    { icon: Minus, title: 'Đường kẻ', id: 'hr' },
    { icon: Strikethrough, title: 'Gạch ngang', id: 'strikethrough' },
    { icon: Link, title: 'Link', id: 'link' },
  ];

  const handleToolClick = useCallback((id) => {
    const actions = {
      bold: () => wrapSelection('**'),
      italic: () => wrapSelection('*'),
      code: () => wrapSelection('`'),
      h2: () => insertAtLineStart('## '),
      h3: () => insertAtLineStart('### '),
      ul: () => insertAtLineStart('- '),
      ol: () => insertAtLineStart('1. '),
      quote: () => insertAtLineStart('> '),
      hr: () => insertAtLineStart('\n---\n'),
      strikethrough: () => wrapSelection('~~'),
      link: () => wrapSelection('[', '](url)'),
    };
    actions[id]?.();
  }, [wrapSelection, insertAtLineStart]);

  return (
    <div className="md-editor">
      {/* Toolbar */}
      <div className="md-editor__toolbar">
        <div className="md-editor__tools">
          {toolsDef.map((tool, i) =>
            tool === 'sep' ? (
              <div key={i} className="md-editor__sep" />
            ) : (
              <button
                key={i}
                type="button"
                className="md-editor__btn"
                title={tool.title}
                onClick={() => handleToolClick(tool.id)}
              >
                <tool.icon size={16} />
              </button>
            )
          )}
        </div>
        <div className="md-editor__mode">
          <button
            type="button"
            className={`md-editor__mode-btn ${mode === 'edit' ? 'active' : ''}`}
            onClick={() => setMode('edit')}
            title="Chỉnh sửa"
          >
            <Pencil size={14} />
            <span>Soạn</span>
          </button>
          <button
            type="button"
            className={`md-editor__mode-btn ${mode === 'preview' ? 'active' : ''}`}
            onClick={() => setMode('preview')}
            title="Xem trước"
          >
            <Eye size={14} />
            <span>Xem</span>
          </button>
        </div>
      </div>

      {/* Content */}
      {mode === 'edit' ? (
        <textarea
          ref={textareaRef}
          className="md-editor__textarea"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          rows={20}
        />
      ) : (
        <div className="md-editor__preview product-description">
          {value ? (
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{value}</ReactMarkdown>
          ) : (
            <p className="text-muted-foreground italic">Chưa có nội dung...</p>
          )}
        </div>
      )}
    </div>
  );
};

export default MarkdownEditor;
