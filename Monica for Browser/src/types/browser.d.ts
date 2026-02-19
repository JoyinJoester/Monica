/// <reference types="chrome" />

// Browser API compatibility types
type MessageSender = chrome.runtime.MessageSender;

declare namespace browser {
  namespace runtime {
    const getURL: (path: string) => string;
    const sendMessage: <T = unknown, R = unknown>(message: T, callback?: (response: R) => void) => boolean;
    const onMessage: {
      addListener: (callback: (message: unknown, sender: MessageSender, sendResponse: (response: unknown) => void) => boolean) => void;
    };
    const onInstalled: {
      addListener: (callback: () => void | Promise<void>) => void;
    };
    const lastError: Error | undefined;
  }

  namespace storage {
    namespace local {
      const get: (keys: string | string[] | null) => Promise<Record<string, unknown>>;
      const set: (items: Record<string, unknown>) => Promise<void>;
    }
  }

  namespace scripting {
    const executeScript: (options: {
      target: { tabId: number };
      files: string[];
    }) => Promise<void>;
  }

  namespace tabs {
    type Tab = chrome.tabs.Tab;
    const create: (createProperties: { url: string }) => Promise<Tab>;
    const query: (queryInfo: object) => Promise<Tab[]>;
  }

  namespace browserAction {
    const openPopup: () => Promise<void>;
  }
}
