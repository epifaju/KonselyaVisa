import { useEffect, type RefObject } from "react";

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

function focusableElements(root: HTMLElement): HTMLElement[] {
  return Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(
    (element) => !element.hasAttribute("disabled") && element.getAttribute("aria-hidden") !== "true",
  );
}

type Options = {
  active: boolean;
  containerRef: RefObject<HTMLElement | null>;
  returnFocusRef?: RefObject<HTMLElement | null>;
  onEscape?: () => void;
};

/** Keeps Tab / Shift+Tab inside `containerRef` while `active`, restores focus on close. */
export function useFocusTrap({ active, containerRef, returnFocusRef, onEscape }: Options) {
  useEffect(() => {
    if (!active) {
      return;
    }
    const container = containerRef.current;
    if (!container) {
      return;
    }

    const previouslyFocused =
      document.activeElement instanceof HTMLElement ? document.activeElement : null;

    const focusFirst = () => {
      const items = focusableElements(container);
      const preferred = items.find((element) => element !== returnFocusRef?.current);
      (preferred ?? items[0] ?? container).focus();
    };
    const frame = window.requestAnimationFrame(() => {
      window.requestAnimationFrame(focusFirst);
    });

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onEscape?.();
        return;
      }
      if (event.key !== "Tab") {
        return;
      }
      const items = focusableElements(container);
      if (items.length === 0) {
        event.preventDefault();
        container.focus();
        return;
      }
      event.preventDefault();
      const activeElement = document.activeElement;
      const currentIndex = items.findIndex((element) => element === activeElement);
      if (event.shiftKey) {
        const previous = currentIndex <= 0 ? items[items.length - 1]! : items[currentIndex - 1]!;
        previous.focus();
        return;
      }
      const next = currentIndex === -1 || currentIndex === items.length - 1 ? items[0]! : items[currentIndex + 1]!;
      next.focus();
    };

    document.addEventListener("keydown", onKeyDown);
    return () => {
      window.cancelAnimationFrame(frame);
      document.removeEventListener("keydown", onKeyDown);
      const restoreTarget = returnFocusRef?.current ?? previouslyFocused;
      if (restoreTarget && document.contains(restoreTarget)) {
        restoreTarget.focus();
      }
    };
  }, [active, containerRef, returnFocusRef, onEscape]);
}
