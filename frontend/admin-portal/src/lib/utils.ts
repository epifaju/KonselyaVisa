import { clsx, type ClassValue } from "clsx";
import { extendTailwindMerge } from "tailwind-merge";

const twMerge = extendTailwindMerge({
  extend: {
    classGroups: {
      "font-size": [
        {
          text: [
            "caption",
            "body-sm",
            "body",
            "body-lg",
            "heading-1",
            "heading-2",
            "heading-3",
            "display",
          ],
        },
      ],
      "text-color": [
        {
          text: [
            "primary-foreground",
            "nav-foreground",
            "destructive-foreground",
            "success-foreground",
            "warning-foreground",
          ],
        },
      ],
    },
  },
});

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
