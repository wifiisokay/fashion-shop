import { clsx } from 'clsx';

const sizes = {
  sm: 'w-4 h-4 border-2',
  md: 'w-6 h-6 border-2',
  lg: 'w-8 h-8 border-3',
  xl: 'w-12 h-12 border-4',
};

const Spinner = ({ size = 'md', className }) => {
  return (
    <div
      className={clsx(
        'border-gray-200 border-t-black rounded-full animate-spin',
        sizes[size],
        className
      )}
    />
  );
};

export default Spinner;
