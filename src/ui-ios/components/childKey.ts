import React from "react";

/**
 * The key for a wrapper around one child of React.Children.toArray: the child's own key (toArray
 * gives '.$<key>' or '.<index>'), so a row leaving the list doesn't remount every row after it
 * (perf audit #30). Falls back to the index for a non-element child.
 */
export function childKey(child: React.ReactNode, index: number): React.Key {
  return (React.isValidElement(child) && child.key) || index;
}
