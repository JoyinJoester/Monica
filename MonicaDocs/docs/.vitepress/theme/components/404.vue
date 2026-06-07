<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from "vue";
import { useData, useRouter, withBase } from "vitepress";

const router = useRouter();
const { lang } = useData();
const ns = "error-page";
const isDropping = ref(false);
let pressTimer: ReturnType<typeof setTimeout> | null = null;

const localeText = {
  zh: {
    title: "\u4f60\u4f3c\u4e4e\u8ff7\u5931\u5728\u672a\u77e5\u7684\u4e16\u754c\u91cc...",
    tip: "\u5f53\u524d\u9875\u9762\u4e0d\u5b58\u5728\u6216\u5df2\u88ab\u79fb\u9664",
    action: "\u8fd4\u56de\u4e3b\u9875",
    imageAlt: "404 \u9875\u9762\u672a\u627e\u5230",
    home: "/",
  },
  en: {
    title: "You seem to be lost in an unknown world...",
    tip: "The current page does not exist or has been removed.",
    action: "Back Home",
    imageAlt: "404 Not Found",
    home: "/en/",
  },
  ja: {
    title: "\u672a\u77e5\u306e\u4e16\u754c\u3067\u9053\u306b\u8ff7\u3063\u305f\u3088\u3046\u3067\u3059...",
    tip: "\u3053\u306e\u30da\u30fc\u30b8\u306f\u5b58\u5728\u3057\u306a\u3044\u304b\u3001\u524a\u9664\u3055\u308c\u305f\u53ef\u80fd\u6027\u304c\u3042\u308a\u307e\u3059\u3002",
    action: "\u30db\u30fc\u30e0\u3078\u623b\u308b",
    imageAlt: "404 \u30da\u30fc\u30b8\u304c\u898b\u3064\u304b\u308a\u307e\u305b\u3093",
    home: "/ja/",
  },
  vi: {
    title: "C\u00f3 v\u1ebb b\u1ea1n \u0111\u00e3 l\u1ea1c v\u00e0o m\u1ed9t th\u1ebf gi\u1edbi ch\u01b0a bi\u1ebft...",
    tip: "Trang hi\u1ec7n t\u1ea1i kh\u00f4ng t\u1ed3n t\u1ea1i ho\u1eb7c \u0111\u00e3 b\u1ecb x\u00f3a.",
    action: "V\u1ec1 trang ch\u1ee7",
    imageAlt: "404 Kh\u00f4ng t\u00ecm th\u1ea5y trang",
    home: "/vi/",
  },
  ru: {
    title: "\u041f\u043e\u0445\u043e\u0436\u0435, \u0432\u044b \u043f\u043e\u0442\u0435\u0440\u044f\u043b\u0438\u0441\u044c \u0432 \u043d\u0435\u0438\u0437\u0432\u0435\u0434\u0430\u043d\u043d\u043e\u043c \u043c\u0438\u0440\u0435...",
    tip: "\u042d\u0442\u0430 \u0441\u0442\u0440\u0430\u043d\u0438\u0446\u0430 \u043d\u0435 \u0441\u0443\u0449\u0435\u0441\u0442\u0432\u0443\u0435\u0442 \u0438\u043b\u0438 \u0431\u044b\u043b\u0430 \u0443\u0434\u0430\u043b\u0435\u043d\u0430.",
    action: "\u041d\u0430 \u0433\u043b\u0430\u0432\u043d\u0443\u044e",
    imageAlt: "404 \u0421\u0442\u0440\u0430\u043d\u0438\u0446\u0430 \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u0430",
    home: "/ru/",
  },
};

const localeKey = computed(() => {
  const value = lang.value.toLowerCase();
  if (value.startsWith("zh")) return "zh";
  if (value.startsWith("ja")) return "ja";
  if (value.startsWith("vi")) return "vi";
  if (value.startsWith("ru")) return "ru";
  return "en";
});

const text = computed(() => localeText[localeKey.value]);

function handleGoHome() {
  clearPressTimer();
  router.go(withBase(text.value.home));
}

function handlePressStart() {
  clearPressTimer();
  pressTimer = setTimeout(() => {
    isDropping.value = true;
  }, 520);
}

function handlePressEnd() {
  clearPressTimer();
  if (!isDropping.value) return;

  window.setTimeout(() => {
    isDropping.value = false;
  }, 620);
}

function clearPressTimer() {
  if (!pressTimer) return;

  clearTimeout(pressTimer);
  pressTimer = null;
}

onBeforeUnmount(clearPressTimer);
</script>

<template>
  <div :class="[ns, 'flx-center']">
    <div :class="`${ns}__avatar-wrapper`">
      <img :src="withBase('/404_NotFound.png')" :class="`${ns}__img`" :alt="text.imageAlt" />
    </div>

    <div :class="[`${ns}__detail`, 'flx-column']">
      <h2 class="glitch-text">404</h2>
      <h4 class="tilt-title">{{ text.title }}</h4>
      <p class="error-tip">{{ text.tip }}</p>

      <button
        @click="handleGoHome"
        @pointerdown="handlePressStart"
        @pointerleave="handlePressEnd"
        @pointerup="handlePressEnd"
        @pointercancel="handlePressEnd"
        class="acgn-btn"
        :class="{ 'is-dropping': isDropping }"
      >
        <span>{{ text.action }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped lang="scss">
$namespace: error-page;

.#{$namespace} {
  width: 100%;
  height: calc(100vh - var(--vp-nav-height));
  gap: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: errorFadeIn 0.6s cubic-bezier(0.25, 1, 0.5, 1) both;

  &__avatar-wrapper {
    position: relative;
    animation: floating 3.5s ease-in-out infinite;
  }

  &__img {
    max-width: 380px;
    height: auto;
    object-fit: contain;
    filter: drop-shadow(0 8px 24px rgba(84, 211, 194, 0.15));
  }

  &__detail {
    display: flex;
    flex-direction: column;
    justify-content: center;

    h2 {
      font-size: 88px;
      font-weight: 900;
      line-height: 1;
      background: linear-gradient(135deg, #54d3c2 0%, #a1c4fd 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      margin: 0;
      font-family: "Arial Black", Gadget, sans-serif;
      transform: rotate(-5deg) skewX(-4deg);
      transform-origin: left bottom;
      animation: numberDrift 4.2s ease-in-out infinite;
    }

    h4 {
      margin: 16px 0 6px 0;
      font-size: 20px;
      font-weight: 600;
      color: var(--vp-c-text-1);
    }

    .tilt-title {
      max-width: 520px;
      transform: rotate(-1.8deg) translateX(-6px);
      transform-origin: left center;
      animation: titleFloat 3.8s ease-in-out infinite;
    }

    .error-tip {
      margin: 0 0 28px 0;
      font-size: 14px;
      color: var(--vp-c-text-2);
      font-style: italic;
      transform: rotate(1.1deg) translateX(8px);
    }

    .acgn-btn {
      position: relative;
      width: auto;
      min-width: 112px;
      height: 34px;
      padding: 0 16px;
      font-size: 13px;
      font-weight: 600;
      color: #171a21;
      background: #54d3c2;
      border: none;
      border-radius: 999px;
      cursor: pointer;
      z-index: 1;
      overflow: hidden;
      transform-origin: center;
      transition: all 0.3s ease;
      box-shadow: 0 4px 12px rgba(84, 211, 194, 0.3);
      animation: buttonWiggle 3.2s ease-in-out infinite;

      span {
        position: relative;
        display: inline-block;
        z-index: 2;
        white-space: nowrap;
        transform-origin: 50% -10px;
        transition: transform 0.22s ease, opacity 0.22s ease;
      }

      &::before {
        content: "";
        position: absolute;
        top: 0;
        left: -100%;
        width: 100%;
        height: 100%;
        background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.4), transparent);
        transition: all 0.5s ease;
      }

      &:hover {
        background: #6fe0d0;
        transform: translateY(-2px) rotate(-1deg);
        box-shadow: 0 6px 18px rgba(84, 211, 194, 0.4);

        &::before {
          left: 100%;
        }
      }

      &:active {
        transform: translateY(1px) rotate(1deg) scale(0.98);
      }

      &.is-dropping span {
        animation: buttonTextDrop 0.62s cubic-bezier(0.2, 0.8, 0.2, 1) both;
      }
    }
  }

  @media (max-width: 768px) {
    flex-direction: column;
    gap: 40px;
    padding: 0 24px;
    text-align: center;

    &__img {
      max-width: 260px;
    }

    &__detail {
      align-items: center;

      h2 {
        font-size: 72px;
        transform-origin: center bottom;
      }

      h4 {
        font-size: 18px;
      }

      .tilt-title,
      .error-tip {
        transform-origin: center;
      }
    }
  }
}

@media (prefers-reduced-motion: reduce) {
  .#{$namespace},
  .#{$namespace}__avatar-wrapper,
  .#{$namespace}__detail h2,
  .#{$namespace}__detail .tilt-title,
  .#{$namespace}__detail .acgn-btn,
  .#{$namespace}__detail .acgn-btn.is-dropping span {
    animation: none;
  }
}

@keyframes floating {
  0% {
    transform: translateY(0px);
  }

  50% {
    transform: translateY(-12px);
  }

  100% {
    transform: translateY(0px);
  }
}

@keyframes errorFadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes numberDrift {
  0%,
  100% {
    transform: rotate(-5deg) skewX(-4deg) translateY(0);
  }

  50% {
    transform: rotate(-2deg) skewX(-4deg) translateY(-6px);
  }
}

@keyframes titleFloat {
  0%,
  100% {
    transform: rotate(-1.8deg) translateX(-6px) translateY(0);
  }

  50% {
    transform: rotate(1deg) translateX(2px) translateY(-4px);
  }
}

@keyframes buttonWiggle {
  0%,
  100% {
    transform: rotate(-0.6deg) translateY(0);
  }

  50% {
    transform: rotate(1.2deg) translateY(-2px);
  }
}

@keyframes buttonTextDrop {
  0% {
    transform: translateY(0) rotate(0deg);
    opacity: 1;
  }

  48% {
    transform: translateY(18px) rotate(7deg);
    opacity: 0;
  }

  49% {
    transform: translateY(-18px) rotate(-7deg);
    opacity: 0;
  }

  100% {
    transform: translateY(0) rotate(0deg);
    opacity: 1;
  }
}
</style>
