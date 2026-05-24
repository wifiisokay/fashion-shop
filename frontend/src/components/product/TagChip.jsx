import { Check, Sparkles } from 'lucide-react';

const TagChip = ({ tag, selected, aiSuggested, disabled, onClick }) => {
  const isAiSuggested = aiSuggested && selected; // Chỉ hiện icon ✨ nếu đang được chọn và là do AI suggest

  let chipStyles = "inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm font-medium transition-colors border ";
  
  if (isAiSuggested) {
    // Selected (AI suggested)
    chipStyles += "bg-purple-600 text-white border-purple-600 hover:bg-purple-700";
  } else if (selected) {
    // Selected (manual)
    chipStyles += "bg-blue-600 text-white border-blue-600 hover:bg-blue-700";
  } else if (disabled) {
    // Unselected (disabled)
    chipStyles += "bg-gray-50 text-gray-400 border-gray-200 cursor-not-allowed opacity-60";
  } else {
    // Unselected
    chipStyles += "bg-white text-gray-700 border-gray-300 hover:border-blue-400 hover:bg-blue-50 cursor-pointer";
  }

  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled && !selected} // Vẫn cho phép click bỏ chọn nếu đang selected
      className={chipStyles}
    >
      {isAiSuggested && <Sparkles className="w-3.5 h-3.5 text-purple-200" />}
      {!isAiSuggested && selected && <Check className="w-3.5 h-3.5" />}
      {tag}
    </button>
  );
};

export default TagChip;
