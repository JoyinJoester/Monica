<script setup lang="ts" name="TeekLayoutProvider">
import Teek from "vitepress-theme-teek";
import ArchivesDashboard from "./ArchivesDashboard.vue";
import GitHubCommitsPanel from "./GitHubCommitsPanel.vue";
import NotFound from "./404.vue";

</script>

<template>
  <Teek.Layout>
    <template #teek-archives-top-before>
      <GitHubCommitsPanel />
      <ArchivesDashboard />
    </template>

    <template #not-found>
      <NotFound />
    </template>
  </Teek.Layout>
</template>

<style lang="scss">
.tk-my.is-circle-bg {
  margin-bottom: 20px;

  .tk-my__avatar.circle-rotate {
    margin-top: 200px;
  }
}
</style>
