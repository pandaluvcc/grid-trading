<template>
  <el-dialog
    v-bind="$attrs"
    :model-value="modelValue"
    :title="title"
    :width="width"
    :top="top"
    :center="center"
    :close-on-click-modal="closeOnClickModal"
    :close-on-press-escape="closeOnPressEscape"
    :show-close="showClose"
    @update:model-value="$emit('update:modelValue', $event)"
    @open="$emit('open')"
    @close="$emit('close')"
    @opened="$emit('opened')"
    @closed="$emit('closed')"
  >
    <slot />
    <template #footer>
      <slot name="footer" />
    </template>
  </el-dialog>
</template>

<script setup>
defineProps({
  modelValue: {
    type: Boolean,
    required: true
  },
  title: {
    type: String,
    default: ''
  },
  width: {
    type: [String, Number],
    default: '50%'
  },
  top: {
    type: String,
    default: '15vh'
  },
  center: {
    type: Boolean,
    default: false
  },
  closeOnClickModal: {
    type: Boolean,
    default: true
  },
  closeOnPressEscape: {
    type: Boolean,
    default: true
  },
  showClose: {
    type: Boolean,
    default: true
  }
})

defineEmits(['update:modelValue', 'open', 'close', 'opened', 'closed'])
</script>

<style scoped>
/* 统一弹窗样式 */
:deep(.el-dialog) {
  border-radius: 12px;
}

:deep(.el-dialog__header) {
  padding: 20px 20px 0;
  font-weight: 600;
  font-size: 18px;
}

:deep(.el-dialog__body) {
  padding: 20px;
  font-size: 14px;
}

:deep(.el-dialog__footer) {
  padding: 0 20px 20px;
}
</style>
