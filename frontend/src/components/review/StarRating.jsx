import { useState } from 'react';

/**
 * StarRating — component hiển thị sao (readonly) hoặc chọn sao (interactive).
 * @param {number} value - Giá trị rating hiện tại (1-5)
 * @param {function} onChange - Callback khi chọn sao (null = readonly)
 * @param {number} size - Kích thước font sao (px)
 * @param {string} color - Màu sao active
 */
export default function StarRating({ value = 0, onChange, size = 20, color = '#facc15' }) {
  const [hover, setHover] = useState(0);
  const interactive = typeof onChange === 'function';

  return (
    <div style={{ display: 'inline-flex', gap: 2, cursor: interactive ? 'pointer' : 'default' }}>
      {[1, 2, 3, 4, 5].map((star) => {
        const filled = star <= (hover || value);
        return (
          <span
            key={star}
            style={{
              fontSize: size,
              color: filled ? color : '#d1d5db',
              transition: 'color 0.15s, transform 0.15s',
              transform: interactive && hover === star ? 'scale(1.2)' : 'scale(1)',
              userSelect: 'none',
            }}
            onClick={() => interactive && onChange(star)}
            onMouseEnter={() => interactive && setHover(star)}
            onMouseLeave={() => interactive && setHover(0)}
          >
            ★
          </span>
        );
      })}
    </div>
  );
}
