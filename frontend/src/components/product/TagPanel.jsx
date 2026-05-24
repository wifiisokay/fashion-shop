import { Sparkles } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import TagChip from './TagChip';

const STYLE_GROUPS = [
  { label: 'Casual / Basic', tags: ['casual', 'basic', 'minimal', 'versatile', 'everyday'] },
  { label: 'Style đặc trưng', tags: ['streetwear', 'smart-casual', 'formal', 'elegant', 'romantic'] },
  { label: 'Form / Dáng', tags: ['fitted', 'loose', 'boxy', 'crop', 'oversized', 'layer', 'slim', 'straight'] },
  { label: 'Chất liệu / Chi tiết', tags: ['embroidery', 'graphic', 'textured', 'knit', 'eco', 'denim', 'nylon', 'detail'] },
  { label: 'Trend / Vibe', tags: ['trendy', 'sporty', 'urban', 'premium', 'cute', 'feminine', 'edgy'] },
];

const OCCASION_GROUPS = [
  { label: 'Hàng ngày', tags: ['daily', 'school', 'work', 'hangout'] },
  { label: 'Đặc biệt', tags: ['date', 'event', 'formal', 'travel'] },
  { label: 'Ngoài trời', tags: ['sport', 'outdoor', 'beach', 'street'] },
  { label: 'Khác', tags: ['winter'] },
];

const TagPanel = ({ 
  label, 
  selectedTags = [], 
  aiSuggestedTags = [], 
  maxCount = 4, 
  isLoadingSuggest = false, 
  onToggle, 
  onSuggestClick, 
  showSuggestBtn = false 
}) => {
  const isMaxReached = selectedTags.length >= maxCount;
  const groups = label.includes('Style') ? STYLE_GROUPS : OCCASION_GROUPS;

  return (
    <div className="bg-gray-50 border border-gray-200 rounded-xl p-4 space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h4 className="font-semibold text-gray-900">{label}</h4>
          <p className="text-sm text-gray-500">Đã chọn {selectedTags.length}/{maxCount}</p>
        </div>
        
        {showSuggestBtn && (
          <Button 
            type="button" 
            size="sm" 
            variant="outline" 
            className="flex items-center gap-2 border-purple-200 hover:bg-purple-50 text-purple-700 hover:text-purple-800"
            onClick={onSuggestClick}
            disabled={isLoadingSuggest}
          >
            {isLoadingSuggest ? (
              <>
                <span className="w-3.5 h-3.5 border-2 border-purple-500 border-t-transparent rounded-full animate-spin" />
                Đang phân tích...
              </>
            ) : (
              <>
                <Sparkles className="w-4 h-4" />
                Gợi ý AI
              </>
            )}
          </Button>
        )}
      </div>

      <div className="space-y-4">
        {groups.map((group) => (
          <div key={group.label} className="space-y-2">
            <div className="text-xs font-medium text-gray-500 uppercase tracking-wider">
              {group.label}
            </div>
            <div className="flex flex-wrap gap-2">
              {group.tags.map((tag) => {
                const isSelected = selectedTags.includes(tag);
                const isAiSuggested = aiSuggestedTags.includes(tag);
                
                return (
                  <TagChip
                    key={tag}
                    tag={tag}
                    selected={isSelected}
                    aiSuggested={isAiSuggested}
                    disabled={isMaxReached}
                    onClick={() => onToggle(tag)}
                  />
                );
              })}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default TagPanel;
