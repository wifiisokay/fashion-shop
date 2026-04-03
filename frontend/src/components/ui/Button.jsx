import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

const variants = {
  primary:   'bg-black text-white hover:bg-gray-800',
  secondary: 'bg-white text-black border border-gray-300 hover:bg-gray-50',
  danger:    'bg-red-500 text-white hover:bg-red-600',
  ghost:     'text-gray-600 hover:text-black hover:bg-gray-100',
};

const sizes = { 
  sm: 'px-3 py-1.5 text-sm', 
  md: 'px-4 py-2 text-sm', 
  lg: 'px-6 py-3 text-base' 
};

const Button = ({ children, variant = 'primary', size = 'md', className, loading, ...props }) => (
  <button 
    className={twMerge(clsx(
      'inline-flex items-center justify-center gap-2 rounded-lg font-medium transition-colors',
      'disabled:opacity-50 disabled:cursor-not-allowed',
      variants[variant], 
      sizes[size], 
      className
    ))} 
    disabled={props.disabled || loading} 
    {...props}
  >
    {loading && <span className="w-4 h-4 border-2 border-current border-t-transparent rounded-full animate-spin" />}
    {children}
  </button>
);

export default Button;
