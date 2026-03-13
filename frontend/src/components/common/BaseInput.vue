<template>
  <el-input
    v-bind="$attrs"
    :model-value="modelValue"
    :size="size"
    :type="type"
    :placeholder="placeholder"
    :disabled="disabled"
    :readonly="readonly"
    :clearable="clearable"
    :show-password="showPassword"
    @update:model-value="$emit('update:modelValue', $event)"
    @focus="$emit('focus', $event)"
    @blur="$emit('blur', $event)"
    @input="$emit('input', $event)"
    @change="$emit('change', $event)"
  >
    <template #prefix>
      <slot name="prefix" />
    </template>
    <template #suffix>
      <slot name="suffix" />
    </template>
    <template #prepend>
      <slot name="prepend" />
    </template>
    <template #append>
      <slot name="append" />
    </template>
  </el-input>
</template>

<script setup>
defineProps({
  modelValue: {
    type: [String, Number],
    default: ''
  },
  size: {
    type: String,
    default: 'default' // small / default / large
  },
  type: {
    type: String,
    default: 'text' // text / number / password / textarea
  },
  placeholder: {
    type: String,
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  },
  readonly: {
    type: Boolean,
    default: false
  },
  clearable: {
    type: Boolean,
    default: false
  },
  showPassword: {
    type: Boolean,
    default: false
  }
})

defineEmits(['update:modelValue', 'focus', 'blur', 'input', 'change'])
</script>

<style scoped>
/* 统一输入框样式 */
:deep(.el-input__wrapper) {
  border-radius: 8px;
  box-shadow: none;
  transition: all 0.2s ease;
}

:deep(.el-input__wrapper:hover) {
  border-color: #409eff;
}

:deep(.el-input__wrapper.is-focus) {
  border-color: #409eff;
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.1);
}

:deep(.el-input__inner) {
  font-size: 14px;
}
</style>
