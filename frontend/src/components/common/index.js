import BaseButton from './BaseButton.vue'
import BaseCard from './BaseCard.vue'
import BaseTag from './BaseTag.vue'
import BaseInput from './BaseInput.vue'
import BaseDialog from './BaseDialog.vue'
import BaseEmpty from './BaseEmpty.vue'

export { BaseButton, BaseCard, BaseTag, BaseInput, BaseDialog, BaseEmpty }

// 全局注册插件
export default {
  install(app) {
    app.component('BaseButton', BaseButton)
    app.component('BaseCard', BaseCard)
    app.component('BaseTag', BaseTag)
    app.component('BaseInput', BaseInput)
    app.component('BaseDialog', BaseDialog)
    app.component('BaseEmpty', BaseEmpty)
  }
}
