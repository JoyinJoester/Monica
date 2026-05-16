<script setup lang="ts">
import { withBase, useRouter } from "vitepress";

const router = useRouter();
const ns = "error-page";
const handleGoHome = () => {
  router.go(withBase('/'));
};
</script>

<template>
  <div :class="[ns, 'flx-center']">
    <div :class="`${ns}__avatar-wrapper`">
      <img :src="withBase('/404_NotFound.png')" :class="`${ns}__img`" alt="404 Not Found" />
    </div>
    
    <div :class="[`${ns}__detail`, 'flx-column']">
      <h2 class="glitch-text">404</h2>
      <h4>你似乎迷失在了未知的世界线... 🗺️</h4>
      <p class="error-tip">当前页面不存在或已被物理抹除</p>
      
      <button @click="handleGoHome" class="acgn-btn">
        <span>返回主界面</span>
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
    }

    h4 {
      margin: 16px 0 6px 0;
      font-size: 20px;
      font-weight: 600;
      color: var(--vp-c-text-1);
    }

    .error-tip {
      margin: 0 0 28px 0;
      font-size: 14px;
      color: var(--vp-c-text-2);
      font-style: italic;
    }
    .acgn-btn {
      position: relative;
      width: 130px;
      height: 38px;
      font-size: 14px;
      font-weight: 600;
      color: #171a21;
      background: #54d3c2;
      border: none;
      border-radius: 4px;
      cursor: pointer;
      z-index: 1;
      overflow: hidden;
      transition: all 0.3s ease;
      box-shadow: 0 4px 12px rgba(84, 211, 194, 0.3);

      span {
        position: relative;
        z-index: 2;
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
        transform: translateY(-2px);
        box-shadow: 0 6px 18px rgba(84, 211, 194, 0.4);

        &::before {
          left: 100%;
        }
      }

      &:active {
        transform: translateY(0);
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
      }
      h4 {
        font-size: 18px;
      }
    }
  }
}
@keyframes floating {
  0% { transform: translateY(0px); }
  50% { transform: translateY(-12px); }
  100% { transform: translateY(0px); }
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
</style>